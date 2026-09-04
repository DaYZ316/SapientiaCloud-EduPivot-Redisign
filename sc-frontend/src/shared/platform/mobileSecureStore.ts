import {registerPlugin} from '@capacitor/core'

interface MobileSecureStorePlugin {
    get(options: {key: string}): Promise<{value?: string}>
    set(options: {key: string; value: string}): Promise<void>
    remove(options: {key: string}): Promise<void>
}

const mobileSecureStore = registerPlugin<MobileSecureStorePlugin>('MobileSecureStore')

export async function readMobileSecureValue(key: string) {
    return (await mobileSecureStore.get({key})).value ?? ''
}

export async function writeMobileSecureValue(key: string, value: string) {
    await mobileSecureStore.set({key, value})
}

export async function removeMobileSecureValue(key: string) {
    await mobileSecureStore.remove({key})
}
