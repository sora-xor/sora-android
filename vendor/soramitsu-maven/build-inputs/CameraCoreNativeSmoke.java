package androidx.camera.core;

import android.graphics.Bitmap;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

public final class CameraCoreNativeSmoke {
  private static ByteBuffer direct(int... values) {
    ByteBuffer buffer = ByteBuffer.allocateDirect(values.length);
    for (int value : values) buffer.put((byte)value);
    buffer.rewind();
    return buffer;
  }
  private static String sha(ByteBuffer buffer) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    for (int i = 0; i < buffer.capacity(); i++) digest.update(buffer.get(i));
    StringBuilder result = new StringBuilder();
    for (byte b : digest.digest()) result.append(String.format("%02x", b & 0xff));
    return result.toString();
  }
  public static void main(String[] args) throws Exception {
    if (args.length != 1) throw new IllegalArgumentException("native SO path required");
    System.load(args[0]);
    ByteBuffer y = direct(16,32,48,64, 80,96,112,128, 144,160,176,192, 208,224,240,255);
    ByteBuffer u = direct(90,110,130,150);
    ByteBuffer v = direct(200,180,160,140);
    Bitmap bitmap = Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888);
    int conversion = ImageProcessingUtil.nativeConvertAndroid420ToBitmap(y,4,u,2,v,2,1,1,
        bitmap,bitmap.getRowBytes(),4,4);
    if (conversion != 0) throw new AssertionError("conversion " + conversion);
    ByteBuffer pixels = ByteBuffer.allocateDirect(bitmap.getByteCount());
    bitmap.copyPixelsToBuffer(pixels);
    String rgbHash = sha(pixels);
    int shift = ImageProcessingUtil.nativeShiftPixel(y,4,u,2,v,2,1,1,4,4,1,1,1);
    if (shift != 0) throw new AssertionError("shift " + shift);
    System.out.println("CAMERA_CORE_JNI_SMOKE_PASS page=" +
        android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE) +
        " rgba=" + rgbHash + " shiftedY=" + sha(y));
  }
}

final class ImageProcessingUtil {
  static native int nativeConvertAndroid420ToBitmap(ByteBuffer y, int strideY,
      ByteBuffer u, int strideU, ByteBuffer v, int strideV, int pixelStrideY,
      int pixelStrideUv, Bitmap bitmap, int bitmapStride, int width, int height);
  static native int nativeShiftPixel(ByteBuffer y, int strideY, ByteBuffer u,
      int strideU, ByteBuffer v, int strideV, int pixelStrideY, int pixelStrideUv,
      int width, int height, int offsetY, int offsetU, int offsetV);
}
