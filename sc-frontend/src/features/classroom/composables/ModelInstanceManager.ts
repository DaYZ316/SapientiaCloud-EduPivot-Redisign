import * as THREE from 'three'

import {getDeskYaw} from '@/features/classroom/composables/useSeatLayout'

interface MeshInfo {
    geometry: THREE.BufferGeometry
    material: THREE.Material | THREE.Material[]
    position: THREE.Vector3
    quaternion: THREE.Quaternion
    scale: THREE.Vector3
    castShadow: boolean
    receiveShadow: boolean
}

export class ModelInstanceManager {
    /** 从场景中提取指定名称的子对象 */
    extractNamedObject(root: THREE.Object3D, name: string): THREE.Object3D | null {
        let found: THREE.Object3D | null = null
        root.traverse((child) => {
            if (child.name === name && !found) {
                found = child
            }
        })
        return found
    }

    collectMeshes(root: THREE.Object3D): MeshInfo[] {
        const meshes: MeshInfo[] = []
        root.updateMatrixWorld(true)
        const rootInverse = root.matrixWorld.clone().invert()
        root.traverse((child) => {
            if (!(child instanceof THREE.Mesh) || !child.geometry) {
                return
            }
            const localMatrix = child.matrixWorld.clone().premultiply(rootInverse)
            const position = new THREE.Vector3()
            const quaternion = new THREE.Quaternion()
            const scale = new THREE.Vector3()
            localMatrix.decompose(position, quaternion, scale)
            meshes.push({
                geometry: child.geometry,
                material: child.material,
                position,
                quaternion,
                scale,
                castShadow: child.castShadow,
                receiveShadow: child.receiveShadow,
            })
        })
        return meshes
    }

    createInstancedMeshes(root: THREE.Object3D, count: number, texture: THREE.Texture | null): THREE.InstancedMesh[] {
        return this.collectMeshes(root).map((mesh, index) => {
            const instancedMesh = new THREE.InstancedMesh(mesh.geometry, this.resolveMaterial(mesh.material, texture), count)
            instancedMesh.name = `desk_${index}_instanced`
            instancedMesh.castShadow = mesh.castShadow
            instancedMesh.receiveShadow = mesh.receiveShadow
            instancedMesh.instanceMatrix.usage = THREE.DynamicDrawUsage
            instancedMesh.userData.basePosition = mesh.position
            instancedMesh.userData.baseQuaternion = mesh.quaternion
            instancedMesh.userData.baseScale = mesh.scale
            return instancedMesh
        })
    }

    setInstanceMatrices(
        instancedMeshes: THREE.InstancedMesh[],
        roomSize: number,
        count: number,
        positionCallback: (deskIndex: number) => THREE.Vector3,
    ) {
        const matrix = new THREE.Matrix4()
        const localMatrix = new THREE.Matrix4()
        const globalMatrix = new THREE.Matrix4()
        const globalScale = new THREE.Vector3(1, 1, 1)

        for (let index = 0; index < count; index += 1) {
            const position = positionCallback(index)
            const rotation = new THREE.Quaternion().setFromEuler(new THREE.Euler(0, getDeskYaw(roomSize, index), 0))
            globalMatrix.compose(position, rotation, globalScale)

            for (const instancedMesh of instancedMeshes) {
                localMatrix.compose(
                    instancedMesh.userData.basePosition as THREE.Vector3,
                    instancedMesh.userData.baseQuaternion as THREE.Quaternion,
                    instancedMesh.userData.baseScale as THREE.Vector3,
                )
                matrix.multiplyMatrices(globalMatrix, localMatrix)
                instancedMesh.setMatrixAt(index, matrix)
            }
        }

        for (const instancedMesh of instancedMeshes) {
            instancedMesh.instanceMatrix.needsUpdate = true
        }
    }

    dispose(instancedMeshes: THREE.InstancedMesh[]) {
        for (const instancedMesh of instancedMeshes) {
            instancedMesh.dispose()
            const material = instancedMesh.material
            if (Array.isArray(material)) {
                material.forEach((item) => item.dispose())
            } else {
                material.dispose()
            }
        }
    }

    /** LARGE教室专用：创建混合实例化网格（第一排和其余排使用不同模型） */
    createMixedInstancedMeshes(
        root: THREE.Object3D,
        firstRowObjectName: string,
        otherRowsObjectName: string,
        firstRowCount: number,
        otherRowsCount: number,
    ): { firstRowMeshes: THREE.InstancedMesh[]; otherRowsMeshes: THREE.InstancedMesh[] } {
        const firstRowObject = this.extractNamedObject(root, firstRowObjectName)
        const otherRowsObject = this.extractNamedObject(root, otherRowsObjectName)

        if (!firstRowObject || !otherRowsObject) {
            console.warn(`Model objects not found: firstRow=${firstRowObjectName}(${firstRowObject ? 'found' : 'not found'}), otherRows=${otherRowsObjectName}(${otherRowsObject ? 'found' : 'not found'})`)
        }

        const firstRowMeshes = firstRowObject ? this.createInstancedMeshes(firstRowObject, firstRowCount, null) : []
        const otherRowsMeshes = otherRowsObject ? this.createInstancedMeshes(otherRowsObject, otherRowsCount, null) : []

        return {firstRowMeshes, otherRowsMeshes}
    }

    /** LARGE教室专用：设置混合实例化网格的矩阵 */
    setMixedInstanceMatrices(
        firstRowMeshes: THREE.InstancedMesh[],
        otherRowsMeshes: THREE.InstancedMesh[],
        roomSize: number,
        firstRowCount: number,
        firstRowStartIndex: number,
        otherRowsStartIndex: number,
        positionCallback: (deskIndex: number) => THREE.Vector3,
    ) {
        const matrix = new THREE.Matrix4()
        const localMatrix = new THREE.Matrix4()
        const globalMatrix = new THREE.Matrix4()
        const globalScale = new THREE.Vector3(1, 1, 1)

        // 设置第一排实例矩阵
        for (let i = 0; i < firstRowCount; i += 1) {
            const deskIndex = firstRowStartIndex + i
            const position = positionCallback(deskIndex)
            const rotation = new THREE.Quaternion().setFromEuler(new THREE.Euler(0, getDeskYaw(roomSize, deskIndex), 0))
            globalMatrix.compose(position, rotation, globalScale)

            for (const instancedMesh of firstRowMeshes) {
                localMatrix.compose(
                    instancedMesh.userData.basePosition as THREE.Vector3,
                    instancedMesh.userData.baseQuaternion as THREE.Quaternion,
                    instancedMesh.userData.baseScale as THREE.Vector3,
                )
                matrix.multiplyMatrices(globalMatrix, localMatrix)
                instancedMesh.setMatrixAt(i, matrix)
            }
        }

        // 设置其余排实例矩阵
        const otherRowsCount = otherRowsMeshes.length > 0 ? (otherRowsMeshes[0].instanceMatrix.count) : 0
        for (let i = 0; i < otherRowsCount; i += 1) {
            const deskIndex = otherRowsStartIndex + i
            const position = positionCallback(deskIndex)
            const rotation = new THREE.Quaternion().setFromEuler(new THREE.Euler(0, getDeskYaw(roomSize, deskIndex), 0))
            globalMatrix.compose(position, rotation, globalScale)

            for (const instancedMesh of otherRowsMeshes) {
                localMatrix.compose(
                    instancedMesh.userData.basePosition as THREE.Vector3,
                    instancedMesh.userData.baseQuaternion as THREE.Quaternion,
                    instancedMesh.userData.baseScale as THREE.Vector3,
                )
                matrix.multiplyMatrices(globalMatrix, localMatrix)
                instancedMesh.setMatrixAt(i, matrix)
            }
        }

        // 更新所有实例矩阵
        for (const instancedMesh of firstRowMeshes) {
            instancedMesh.instanceMatrix.needsUpdate = true
        }
        for (const instancedMesh of otherRowsMeshes) {
            instancedMesh.instanceMatrix.needsUpdate = true
        }
    }

    private resolveMaterial(material: THREE.Material | THREE.Material[], texture: THREE.Texture | null) {
        if (texture) {
            texture.colorSpace = THREE.SRGBColorSpace
            texture.flipY = false
            return new THREE.MeshBasicMaterial({
                map: texture,
                side: THREE.DoubleSide,
            })
        }
        if (Array.isArray(material)) {
            return material.map((item) => item.clone())
        }
        return material.clone()
    }
}
