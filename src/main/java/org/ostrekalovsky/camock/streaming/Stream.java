package org.ostrekalovsky.camock.streaming;

import org.ostrekalovsky.camock.ImageRepository;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Created by Oleg Strekalovsky on 23.08.2016.
 */
public class Stream {

    private static final String MULTIPART_BOUNDARY = "JPEG_FRAME_BOUNDARY";
    private volatile Camera camera = null;
    private final ImageRepository repository;

    public Stream(String imageId, int rotation, int maxFPS, HttpServletRequest req, HttpServletResponse response, ImageRepository repository) throws IOException {
        this.repository = repository;
        AsyncContext context = req.startAsync();
        context.setTimeout(0);
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setContentType("multipart/x-mixed-replace; boundary=" + MULTIPART_BOUNDARY);
        response.setHeader("Connection", "keep-alive");
        AsyncRequest asyncRequest = new AsyncRequest(context);

        camera = Camera.getCameraInstance(repository, "photo-panda.jpeg", rotation / 90,maxFPS, (frame) -> {
            try {
                // camera.setRotation((this.rotation += 90) % 360);
                System.out.println("frame size:" + frame.length);
                asyncRequest.onWriteWhenPossible(buildMJPEGFrame(frame));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                    if (asyncRequest.isDone.get()) {
                        if (camera != null) {
                            camera.stop();
                            return;
                        }
                    }
                } catch (InterruptedException e) {
                    if (camera != null) {
                        camera.stop();
                        return;
                    }
                }
            }
        }).start();

    }

    public void setImageId(String imageId) {
        camera.setImage(imageId);
    }

    public void setMaxFPS(int fps) {
        camera.setMaxFps(fps);
    }

    public void setRotation(int rotation) {
        camera.setRotation(rotation);
    }

    private byte[] buildMJPEGFrame(byte[] currentFrame) {
        byte[] p1 = ("--" + MULTIPART_BOUNDARY + "\r\n" + "Content-Type: image/jpeg" + "\r\n" + "Content-Length: "
                + currentFrame.length + "\r\n" + "\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] p2 = ("\r\n").getBytes(StandardCharsets.UTF_8);
        int size = p1.length + p2.length + currentFrame.length;
        byte[] pckg = new byte[size];
        System.arraycopy(p1, 0, pckg, 0, p1.length);
        System.arraycopy(currentFrame, 0, pckg, p1.length, currentFrame.length);
        System.arraycopy(p2, 0, pckg, p1.length + currentFrame.length, p2.length);
        return pckg;
    }
}