/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: C:\\Users\\kotpe\\AppData\\Local\\Android\\Sdk\\build-tools\\36.0.0\\aidl.exe -pC:\\Users\\kotpe\\AppData\\Local\\Android\\Sdk\\platforms\\android-36\\framework.aidl -oC:\\Users\\kotpe\\OneDrive\\Документы\\New\\ project\\ 25\\ToolNeuron-re-write\\santiya-localai-sdk\\build\\generated\\aidl_source_output_dir\\debug\\out -IC:\\Users\\kotpe\\OneDrive\\Документы\\New\\ project\\ 25\\ToolNeuron-re-write\\santiya-localai-sdk\\src\\main\\aidl -IC:\\Users\\kotpe\\OneDrive\\Документы\\New\\ project\\ 25\\ToolNeuron-re-write\\santiya-localai-sdk\\src\\debug\\aidl -IC:\\tmp\\gradle-santiya\\caches\\9.3.1\\transforms\\860e74d61cb564d1a19108028201557f\\workspace\\transformed\\core-1.17.0\\aidl -IC:\\tmp\\gradle-santiya\\caches\\9.3.1\\transforms\\fb86f212e8dba4582539050ae7ff22d3\\workspace\\transformed\\versionedparcelable-1.1.1\\aidl -dC:\\Users\\kotpe\\AppData\\Local\\Temp\\aidl16715906897981141891.d C:\\Users\\kotpe\\OneDrive\\Документы\\New\\ project\\ 25\\ToolNeuron-re-write\\santiya-localai-sdk\\src\\main\\aidl\\com\\santiya\\localaihub\\service\\IDiffusionGenerationCallback.aidl
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package com.santiya.localaihub.service;
public interface IDiffusionGenerationCallback extends android.os.IInterface
{
  /** Default implementation for IDiffusionGenerationCallback. */
  public static class Default implements com.santiya.localaihub.service.IDiffusionGenerationCallback
  {
    @Override public void onProgress(float progress, int currentStep, int totalSteps, java.lang.String intermediateImageBase64) throws android.os.RemoteException
    {
    }
    @Override public void onComplete(java.lang.String imageBase64, long seed, int width, int height) throws android.os.RemoteException
    {
    }
    @Override public void onError(java.lang.String message) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.santiya.localaihub.service.IDiffusionGenerationCallback
  {
    /** Construct the stub and attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.santiya.localaihub.service.IDiffusionGenerationCallback interface,
     * generating a proxy if needed.
     */
    public static com.santiya.localaihub.service.IDiffusionGenerationCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.santiya.localaihub.service.IDiffusionGenerationCallback))) {
        return ((com.santiya.localaihub.service.IDiffusionGenerationCallback)iin);
      }
      return new com.santiya.localaihub.service.IDiffusionGenerationCallback.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      if (code == INTERFACE_TRANSACTION) {
        reply.writeString(descriptor);
        return true;
      }
      switch (code)
      {
        case TRANSACTION_onProgress:
        {
          float _arg0;
          _arg0 = data.readFloat();
          int _arg1;
          _arg1 = data.readInt();
          int _arg2;
          _arg2 = data.readInt();
          java.lang.String _arg3;
          _arg3 = data.readString();
          this.onProgress(_arg0, _arg1, _arg2, _arg3);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_onComplete:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          long _arg1;
          _arg1 = data.readLong();
          int _arg2;
          _arg2 = data.readInt();
          int _arg3;
          _arg3 = data.readInt();
          this.onComplete(_arg0, _arg1, _arg2, _arg3);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_onError:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.onError(_arg0);
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.santiya.localaihub.service.IDiffusionGenerationCallback
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public void onProgress(float progress, int currentStep, int totalSteps, java.lang.String intermediateImageBase64) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeFloat(progress);
          _data.writeInt(currentStep);
          _data.writeInt(totalSteps);
          _data.writeString(intermediateImageBase64);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onProgress, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void onComplete(java.lang.String imageBase64, long seed, int width, int height) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(imageBase64);
          _data.writeLong(seed);
          _data.writeInt(width);
          _data.writeInt(height);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onComplete, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void onError(java.lang.String message) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(message);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onError, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onProgress = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onComplete = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_onError = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "com.santiya.localaihub.service.IDiffusionGenerationCallback";
  public void onProgress(float progress, int currentStep, int totalSteps, java.lang.String intermediateImageBase64) throws android.os.RemoteException;
  public void onComplete(java.lang.String imageBase64, long seed, int width, int height) throws android.os.RemoteException;
  public void onError(java.lang.String message) throws android.os.RemoteException;
}
