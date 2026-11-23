import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

public class Installer {
    public static void main(String[] args) throws Exception {
        android.os.Looper.prepare();
        String apk = args[0];
        Class<?> atc = Class.forName("android.app.ActivityThread");
        Method sm = atc.getDeclaredMethod("systemMain");
        sm.setAccessible(true);
        Object at = sm.invoke(null);
        Method gsc = atc.getDeclaredMethod("getSystemContext");
        gsc.setAccessible(true);
        Context ctx = (Context) gsc.invoke(at);

        Method gpi = ctx.getClass().getMethod("getPackageInstaller");
        gpi.setAccessible(true);
        PackageInstaller pi = (PackageInstaller) gpi.invoke(ctx);
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName("com.relaycoord.test");
        int id = pi.createSession(params);
        PackageInstaller.Session session = pi.openSession(id);
        OutputStream out = session.openWrite("base.apk", 0, -1);
        InputStream in = new FileInputStream(apk);
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        out.flush();
        session.fsync(out);
        out.close();

        Intent done = new Intent("com.relaycoord.test.INSTALL_DONE");
        PendingIntent receiver = PendingIntent.getBroadcast(ctx, 0, done,
                PendingIntent.FLAG_UPDATE_CURRENT);
        session.commit(receiver.getIntentSender());
        System.out.println("committed session " + id);

        for (int i = 0; i < 40; i++) {
            Thread.sleep(500);
            if (pi.getSessionInfo(id) == null) break;
        }
        try {
            PackageInfo info = ctx.getPackageManager().getPackageInfo("com.relaycoord.test", 0);
            System.out.println("INSTALLED " + info.versionName);
        } catch (Exception e) {
            System.out.println("NOT INSTALLED: " + e);
        }
    }
}
