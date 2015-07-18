package com.gxwtech.bleach;

import android.content.Context;

import org.droidparts.AbstractDependencyProvider;
import org.droidparts.persist.sql.AbstractDBOpenHelper;

import no.nordicsemi.puckcentral.bluetooth.gatt.GattManager;

public class DependencyProvider extends AbstractDependencyProvider{

    private final Context mContext;
    private final GattManager mGattManager;

    @Override
    public AbstractDBOpenHelper getDBOpenHelper() {
        //return mDBOpenHelper;
        return null; // why do I have to have a DBOpenHelper?
    }


    public DependencyProvider(Context ctx) {
        super(ctx);
        mGattManager = new GattManager();
        mContext = ctx;
    }

    @Override
    public Context getContext() {
        return mContext;
    }
    public GattManager getGattManager() {
        return mGattManager;
    }
}
