import {Capacitor} from '@capacitor/core'

export function isMobileApp() {
    return Capacitor.isNativePlatform()
}
