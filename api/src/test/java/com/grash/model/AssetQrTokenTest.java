package com.grash.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssetQrTokenTest {

    @Test
    void ensureQrTokenGeneratesOpaqueToken() {
        Asset asset = new Asset();
        asset.setName("Ventilator 42");

        asset.ensureQrToken();

        assertNotNull(asset.getQrToken());
        assertEquals(32, asset.getQrToken().length());
        assertTrue(asset.getQrToken().matches("[0-9a-f]{32}"));
        assertFalse(asset.getQrToken().contains("Ventilator"));
    }

    @Test
    void ensureQrTokenIsStableWhenAlreadyGenerated() {
        Asset asset = new Asset();
        asset.ensureQrToken();
        String first = asset.getQrToken();

        asset.ensureQrToken();

        assertEquals(first, asset.getQrToken());
    }

    @Test
    void ensureQrTokenReplacesBlankToken() {
        Asset asset = new Asset();
        asset.setQrToken("   ");

        asset.ensureQrToken();

        assertNotNull(asset.getQrToken());
        assertEquals(32, asset.getQrToken().length());
    }

    @Test
    void twoAssetsGetDifferentTokens() {
        Asset first = new Asset();
        Asset second = new Asset();
        first.ensureQrToken();
        second.ensureQrToken();

        assertNotEquals(first.getQrToken(), second.getQrToken());
    }

    @Test
    void newAssetDefaultsToSafeMetadata() {
        Asset asset = new Asset();

        assertEquals(com.grash.model.enums.Criticality.MEDIUM, asset.getCriticality());
        assertEquals(com.grash.model.enums.EquipmentType.OTHER, asset.getEquipmentType());
        assertEquals(com.grash.model.enums.ClinicalImpact.NON_CLINICAL, asset.getClinicalImpact());
    }
}
