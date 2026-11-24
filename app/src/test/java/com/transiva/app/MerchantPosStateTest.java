package com.transiva.app;

import org.junit.Test;

import static org.junit.Assert.*;

public class MerchantPosStateTest {
    @Test public void keepsBasketWhenStockChangedAndLetsCashierRemoveUnavailableItem() {
        MerchantPosBasket cart=new MerchantPosBasket();
        assertTrue(cart.change("product",2,quantity->quantity<=2));
        assertFalse(cart.change("product",1,quantity->quantity<=1));
        assertEquals(2,cart.quantity("product"));
        assertTrue(cart.change("product",-2,quantity->false));
        assertTrue(cart.isEmpty());
    }

    @Test public void continuousCameraIgnoresHeldBarcodeAndAcceptsNextProduct() {
        MerchantPosCamera camera=new MerchantPosCamera();
        assertTrue(camera.accepts("123",1000));
        assertFalse(camera.accepts("123",1500));
        assertTrue(camera.accepts("456",1510));
        assertTrue(camera.accepts("123",1520));
        assertFalse(camera.accepts("123",2000));
        assertTrue(camera.accepts("123",2950));
        camera.reset();
        assertTrue(camera.accepts("123",2951));
    }
}
