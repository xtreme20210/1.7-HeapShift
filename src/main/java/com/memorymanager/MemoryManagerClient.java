
package com.memorymanager;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.lang.management.ManagementFactory;
import org.lwjgl.opengl.Display;
import net.fabricmc.api.ClientModInitializer;

public class MemoryManagerClient implements ClientModInitializer {

    private static final String SOFT_MAX = "SoftMaxHeapSize";
    private static final long BACKGROUND_TARGET = 600L * 1024 * 1024;
    private static final long CHECK_INTERVAL_MS = 250;

    @Override
    public void onInitializeClient() {

        HotSpotDiagnosticMXBean bean =
                ManagementFactory.getPlatformMXBean(
                        HotSpotDiagnosticMXBean.class
                );

        if (bean == null) {
            System.err.println("[MemoryManager] HotSpot management unavailable.");
            return;
        }

        try {
            boolean zgcEnabled = Boolean.parseBoolean(
                    bean.getVMOption("UseZGC").getValue()
            );

            if (!zgcEnabled || !bean.getVMOption(SOFT_MAX).isWriteable()) {
                System.err.println(
                        "[MemoryManager] ZGC or dynamic soft limit unavailable."
                );
                return;
            }

            long maxHeap = Long.parseLong(
                    bean.getVMOption("MaxHeapSize").getValue()
            );

            long backgroundHeap = Math.min(BACKGROUND_TARGET, maxHeap);

            Thread monitor = new Thread(() -> {
                Boolean lastFocus = null;

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        if (Display.isCreated()) {
                            boolean focused = Display.isActive();

                            if (lastFocus == null || focused != lastFocus) {
                                long target = focused ? maxHeap : backgroundHeap;

                                bean.setVMOption(
                                        SOFT_MAX,
                                        Long.toString(target)
                                );

                                System.out.println(
                                    "[MemoryManager] Verified SoftMaxHeapSize: " +
                                    bean.getVMOption(SOFT_MAX).getValue()
                                );

                                System.out.println(
                                        "[MemoryManager] " +
                                        (focused ? "FOCUSED" : "BACKGROUND") +
                                        " | SoftMaxHeapSize = " +
                                        target / (1024 * 1024) + " MiB"
                                );

                                lastFocus = focused;
                            }
                        }

                        Thread.sleep(CHECK_INTERVAL_MS);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        System.err.println(
                                "[MemoryManager] Controller stopped: " + e
                        );
                        return;
                    }
                }
            }, "MemoryManager-Monitor");

            monitor.setDaemon(true);
            monitor.start();

            System.out.println("[MemoryManager] Controller initialized.");

        } catch (Exception e) {
            System.err.println(
                    "[MemoryManager] Initialization failed: " + e
            );
        }
    }
}

