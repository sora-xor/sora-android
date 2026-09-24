import android.graphics.Path;
import androidx.graphics.path.PathIterator;
import androidx.graphics.path.PathSegment;

public final class GraphicsPathSmoke {
  public static void main(String[] args) {
    if (args.length != 1) throw new IllegalArgumentException("native library path required");
    System.load(args[0]);
    Path path = new Path();
    path.moveTo(1f, 2f);
    path.lineTo(3f, 4f);
    path.quadTo(5f, 6f, 7f, 8f);
    path.cubicTo(9f, 10f, 11f, 12f, 13f, 14f);
    path.close();
    PathIterator iterator = new PathIterator(path, PathIterator.ConicEvaluation.AsConic, 0.25f);
    if (iterator.calculateSize(true) != 5) throw new AssertionError("size " + iterator.calculateSize(true));
    StringBuilder verbs = new StringBuilder();
    float[] points = new float[8];
    int count = 0;
    while (iterator.hasNext()) {
      PathSegment.Type type = iterator.next(points, 0);
      if (count++ > 0) verbs.append(',');
      verbs.append(type.name());
      if (count == 1 && (points[0] != 1f || points[1] != 2f)) throw new AssertionError("move points");
      if (count == 2 && (points[2] != 3f || points[3] != 4f)) throw new AssertionError("line points");
    }
    String result = verbs.toString();
    if (!result.equals("Move,Line,Quadratic,Cubic,Close")) throw new AssertionError(result);
    System.out.println("GRAPHICS_PATH_JNI_SMOKE_PASS page=" + android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE) + " verbs=" + result);
  }
}
