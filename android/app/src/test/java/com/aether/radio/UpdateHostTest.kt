package com.aether.radio

import org.junit.Assert.*
import org.junit.Test
import java.net.URL

class UpdateHostTest {
    @Test fun redirectsStayOnExplicitHttpsHosts(){
        assertTrue(Updates.allowed(URL("https://release-assets.githubusercontent.com/asset")))
        assertFalse(Updates.allowed(URL("http://github.com/dpan538/Aether")))
        assertFalse(Updates.allowed(URL("https://github.com.attacker.invalid/asset")))
        assertFalse(Updates.allowed(URL("https://github.com:8443/asset")))
        assertFalse(Updates.allowed(URL("https://user@github.com/asset")))
    }
    @Test fun initialAssetsBelongToThisRepository(){
        Updates.requireRepoAsset("https://github.com/dpan538/Aether/releases/download/android-v1/Aether.apk")
        for(url in listOf("https://github.com/other/Aether/releases/download/v1/a.apk","https://github.com/dpan538/Other/releases/download/v1/a.apk","https://objects.githubusercontent.com/direct.apk")){
            try{Updates.requireRepoAsset(url);fail("Foreign repository accepted")}catch(expected:IllegalArgumentException){}
        }
    }
}
