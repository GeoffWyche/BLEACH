package com.gxwtech.bleach;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.widget.Button;
import android.widget.TextView;

/**
 * Created by geoff on 7/14/15.
 */
public class CharaWidget extends TextView {
    protected int mLabelId;
    protected int mValueFieldId;
    protected int mButtonId;
    protected Context mCtx;
    protected BluetoothGattCharacteristic mChara;

    public CharaWidget(Context context, int labelId, int valueFieldId, int buttonId) {
        super(context);
        mLabelId = labelId;
        mValueFieldId = valueFieldId;
        mButtonId = buttonId;
    }

    public void setChara(BluetoothGattCharacteristic chara) {
        mChara = chara;
    }

    public void updateValueField(String text) {
        ((TextView)findViewById(mValueFieldId)).setText(text);
    }

    public void setLabel(String label) {
        ((TextView) findViewById(mLabelId)).setText(label);
    }

    public void setOnClick(OnClickListener ocl) {
        ((Button)findViewById(mButtonId)).setOnClickListener(ocl);
    }
}
