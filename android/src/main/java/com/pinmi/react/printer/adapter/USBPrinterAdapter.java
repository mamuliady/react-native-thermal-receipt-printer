package com.pinmi.react.printer.adapter;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;


import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xiesubin on 2017/9/20.
 */

public class USBPrinterAdapter implements PrinterAdapter {
    private static USBPrinterAdapter mInstance;


    private String LOG_TAG = "RNUSBPrinter";
    private Context mContext;
    private UsbManager mUSBManager;
    private PendingIntent mPermissionIndent;
    private UsbDevice mUsbDevice;
    private UsbDeviceConnection mUsbDeviceConnection;
    private UsbInterface mUsbInterface;
    private UsbEndpoint mEndPoint;
    private static final String ACTION_USB_PERMISSION = "com.pinmi.react.USBPrinter.USB_PERMISSION";
    private static final String EVENT_USB_DEVICE_ATTACHED = "usbAttached";
    private Callback successCallback;
    private Callback errorCallback;


    private USBPrinterAdapter() {
    }

    public static USBPrinterAdapter getInstance() {
        if (mInstance == null) {
            mInstance = new USBPrinterAdapter();
        }
        return mInstance;
    }

    private final BroadcastReceiver mUsbDeviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.i(LOG_TAG, "success to grant permission for device " + usbDevice.getDeviceId() + ", vendor_id: " + usbDevice.getVendorId() + " product_id: " + usbDevice.getProductId());
                        mUsbDevice = usbDevice;
                    } else {
                        Toast.makeText(context, "User refuses to obtain USB device permissions" + usbDevice.getDeviceName(), Toast.LENGTH_LONG).show();
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                if (mUsbDevice != null) {
                    Toast.makeText(context, "USB device has been turned off", Toast.LENGTH_LONG).show();
                    closeConnectionIfExists();
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action) || UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Toast.makeText(context, "USB device Attached", Toast.LENGTH_LONG).show();
                UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    Log.i(LOG_TAG, "success to grant permission for device " + usbDevice.getDeviceId() + ", vendor_id: " + usbDevice.getVendorId() + " product_id: " + usbDevice.getProductId());
                    mUsbDevice = usbDevice;
                } else {
                    Toast.makeText(context, "User refuses to obtain USB device permissions" + usbDevice.getDeviceName(), Toast.LENGTH_LONG).show();
                }

                // init(mContext, successCallback, errorCallback);
            
                synchronized (this) {
                    if (mContext != null) {
                        ((ReactApplicationContext) mContext).getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                                .emit(EVENT_USB_DEVICE_ATTACHED, null);
                    }
                }
            }
        }
    };

    public void init(ReactApplicationContext reactContext, Callback successCallback, Callback errorCallback) {
        this.mContext = reactContext;
        this.mUSBManager = (UsbManager) this.mContext.getSystemService(Context.USB_SERVICE);
        this.mPermissionIndent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
        this.errorCallback = errorCallback;
        this.successCallback = successCallback;
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        mContext.registerReceiver(mUsbDeviceReceiver, filter);
        Log.v(LOG_TAG, "RNUSBPrinter initialized");
        successCallback.invoke();
    }


    public void closeConnectionIfExists() {
        if (mUsbDeviceConnection != null) {
            mUsbDeviceConnection.releaseInterface(mUsbInterface);
            mUsbDeviceConnection.close();
            mUsbInterface = null;
            mEndPoint = null;
            mUsbDeviceConnection = null;
            // mContext.unregisterReceiver(mUsbDeviceReceiver);

        }
    }

    public List<PrinterDevice> getDeviceList(Callback errorCallback) {
        List<PrinterDevice> lists = new ArrayList<>();
        if (mUSBManager == null) {
            errorCallback.invoke("USBManager is not initialized while get device list");
            return lists;
        }

        for (UsbDevice usbDevice : mUSBManager.getDeviceList().values()) {
            lists.add(new USBPrinterDevice(usbDevice));
        }
        return lists;
    }


    @Override
    public void selectDevice(PrinterDeviceId printerDeviceId, Callback successCallback, Callback errorCallback) {
        if (mUSBManager == null) {
            errorCallback.invoke("USBManager is not initialized before select device");
            Toast.makeText(mContext, "USBManager is not initialized before select device", Toast.LENGTH_LONG).show();
            return;
        }

        USBPrinterDeviceId usbPrinterDeviceId = (USBPrinterDeviceId) printerDeviceId;
        // if (mUsbDevice != null && mUsbDevice.getVendorId() == usbPrinterDeviceId.getVendorId() && mUsbDevice.getProductId() == usbPrinterDeviceId.getProductId()) {
        //     Toast.makeText(mContext, "already selected device, do not need repeat to connect", Toast.LENGTH_LONG).show();

        //     Log.i(LOG_TAG, "already selected device, do not need repeat to connect");
        //     if(!mUSBManager.hasPermission(mUsbDevice)){
        //         closeConnectionIfExists();
        //         mUSBManager.requestPermission(mUsbDevice, mPermissionIndent);
        //     }
        //     successCallback.invoke(new USBPrinterDevice(mUsbDevice).toRNWritableMap());
        //     return;
        // }
        closeConnectionIfExists();
        if (mUSBManager.getDeviceList().size() == 0) {
            Toast.makeText(mContext, "Device list is empty, can not choose device", Toast.LENGTH_LONG).show();
            errorCallback.invoke("Device list is empty, can not choose device");
            return;
        }
        for (UsbDevice usbDevice : mUSBManager.getDeviceList().values()) {
            if (usbDevice.getVendorId() == usbPrinterDeviceId.getVendorId() && usbDevice.getProductId() == usbPrinterDeviceId.getProductId()) {
                Log.v(LOG_TAG, "request for device: vendor_id: " + usbPrinterDeviceId.getVendorId() + ", product_id: " + usbPrinterDeviceId.getProductId());

                closeConnectionIfExists();
                mUSBManager.requestPermission(usbDevice, mPermissionIndent);
                successCallback.invoke(new USBPrinterDevice(usbDevice).toRNWritableMap());
                return;
            }
        }
        Toast.makeText(mContext, "can not find specified device", Toast.LENGTH_LONG).show();

        errorCallback.invoke("can not find specified device");
        return;
    }

    private boolean openConnection() {
        if (mUsbDevice == null) {
            Log.e(LOG_TAG, "USB Deivce is not initialized");
            Toast.makeText(mContext, "USB Deivce is not initialized", Toast.LENGTH_LONG).show();
            return false;
        }
        if (mUSBManager == null) {
            Log.e(LOG_TAG, "USB Manager is not initialized");
            Toast.makeText(mContext, "USB Manager is not initialized", Toast.LENGTH_LONG).show();
            return false;
        }

        if (mUsbDeviceConnection != null) {
            Log.i(LOG_TAG, "USB Connection already connected");
            // Toast.makeText(mContext, "USB Connection already connected", Toast.LENGTH_LONG).show();
            return true;
        }

        int interfaceCount = mUsbDevice.getInterfaceCount();
        // Toast.makeText(mContext, "Interface Count" + interfaceCount, Toast.LENGTH_SHORT).show();


        for(int x = 0; x < interfaceCount; x++) {
            UsbInterface usbInterface = mUsbDevice.getInterface(x);
            for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                final UsbEndpoint ep = usbInterface.getEndpoint(i);
                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                        UsbDeviceConnection usbDeviceConnection = mUSBManager.openDevice(mUsbDevice);
                        if (usbDeviceConnection == null) {
                            Log.e(LOG_TAG, "failed to open USB Connection");
                            // Toast.makeText(mContext, "failed to open USB Connection", Toast.LENGTH_SHORT).show();
                            // return false;
                        } else {
                            if (usbDeviceConnection.claimInterface(usbInterface, true)) {

                                mEndPoint = ep;
                                mUsbInterface = usbInterface;
                                mUsbDeviceConnection = usbDeviceConnection;
                                Log.i(LOG_TAG, "Device connected");
                                // Toast.makeText(mContext, "Device connected", Toast.LENGTH_LONG).show();
                                return true;
                            } else {
                                usbDeviceConnection.close();
                                Log.e(LOG_TAG, "failed to claim usb connection");
                                // Toast.makeText(mContext, "failed to claim usb connection", Toast.LENGTH_LONG).show();
                                // return false;
                            }
                        }
                        
                    }
                }
            }
        }
        
        return false;
        // return true;
    }


    public void printRawData(String data, Callback errorCallback) {
        final String rawData = data;
        Log.v(LOG_TAG, "start to print raw data " + data);
        boolean isConnected = openConnection();
        if (isConnected) {
            Log.v(LOG_TAG, "Connected to device");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] bytes = Base64.decode(rawData, Base64.DEFAULT);
                    int b = mUsbDeviceConnection.bulkTransfer(mEndPoint, bytes, bytes.length, 100000);
                    Log.i(LOG_TAG, "Return Status: b-->" + b);
                }
            }).start();
        } else {
            String msg = "failed to connected to device";
            // Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();
            Log.v(LOG_TAG, msg);
            errorCallback.invoke(msg);
        }
    }


}
