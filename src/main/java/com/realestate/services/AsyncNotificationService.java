package com.realestate.services;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service demonstrating Java Multithreading and Synchronization.
 * Implements a thread-safe asynchronous background notification and audit worker
 * using producer-consumer pattern with synchronized queue management.
 */
public final class AsyncNotificationService {

    public static class NotificationTask {
        private final String recipient;
        private final String subject;
        private final String message;
        private final long timestamp;

        public NotificationTask(String recipient, String subject, String message) {
            this.recipient = recipient;
            this.subject = subject;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }

        public String getRecipient() { return recipient; }
        public String getSubject() { return subject; }
        public String getMessage() { return message; }
        public long getTimestamp() { return timestamp; }
    }

    private static final int MAX_QUEUE_CAPACITY = 500;
    private static volatile AsyncNotificationService instance;

    private final BlockingQueue<NotificationTask> taskQueue;
    private final Thread workerThread;
    private final AtomicInteger processedTaskCount;
    private volatile boolean running;

    private AsyncNotificationService() {
        this.taskQueue = new LinkedBlockingQueue<>(MAX_QUEUE_CAPACITY);
        this.processedTaskCount = new AtomicInteger(0);
        this.running = true;

        // Dedicated background daemon thread
        this.workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                processQueue();
            }
        }, "RealEstate-Notification-Worker");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    public static AsyncNotificationService getInstance() {
        if (instance == null) {
            synchronized (AsyncNotificationService.class) {
                if (instance == null) {
                    instance = new AsyncNotificationService();
                }
            }
        }
        return instance;
    }

    /**
     * Thread-safe synchronized dispatch method.
     */
    public synchronized boolean dispatchNotification(String recipient, String subject, String message) {
        if (!running) return false;
        NotificationTask task = new NotificationTask(recipient, subject, message);
        boolean offered = taskQueue.offer(task);
        if (!offered) {
            System.err.println("[AsyncNotificationService] Queue full! Dropping notification for: " + recipient);
        }
        return offered;
    }

    /**
     * Background consumer loop processing tasks asynchronously without blocking HTTP requests.
     */
    private void processQueue() {
        while (running) {
            try {
                NotificationTask task = taskQueue.take();
                // Simulate asynchronous email/SMS delivery logging
                int count = processedTaskCount.incrementAndGet();
                System.out.println("[BACKGROUND NOTIFICATION #" + count + "] To: " + task.getRecipient() + 
                                   " | Subject: " + task.getSubject() + " | Body: " + task.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[AsyncNotificationService] Error executing notification task: " + e.getMessage());
            }
        }
    }

    public int getProcessedCount() {
        return processedTaskCount.get();
    }

    public synchronized void shutdown() {
        this.running = false;
        this.workerThread.interrupt();
    }
}
