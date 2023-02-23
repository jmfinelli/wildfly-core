package org.jboss.as.server.suspend;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.as.controller.notification.NotificationHandlerRegistry;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * The graceful shutdown controller. This class co-ordinates the graceful shutdown and pause/resume of a
 * servers operations.
 * <p/>
 * <p/>
 * In most cases this work is delegated to the request controller subsystem.
 * however for workflows that do no correspond directly to a request model a {@link ServerActivity} instance
 * can be registered directly with this controller.
 *
 * @author Stuart Douglas
 */
public class SuspendController implements Service<SuspendController> {

    /**
     * Timer that handles the timeout. We create it on pause, rather than leaving it hanging round.
     */
    private Timer timer;

    private State state = State.SUSPENDED;

    private ExecutorService executorService;

    private final Deque<ServerActivity> activities = new ArrayDeque<>();

    private final List<OperationListener> operationListeners = new ArrayList<>();

    private final InjectedValue<NotificationHandlerRegistry> notificationHandlerRegistry = new InjectedValue<>();

    private AtomicInteger outstandingCount;

    private boolean startSuspended;

    private final ServerActivityCallback listener = this::activityPaused;

    public SuspendController() {
        this.startSuspended = false;
    }

    public void setStartSuspended(boolean startSuspended) {
        //TODO: it is not very clear what this boolean stands for now.
        this.startSuspended = startSuspended;
        state = State.SUSPENDED;
    }

    public synchronized void suspend(long timeoutMillis) {
        if(state == State.SUSPENDED) {
            return;
        }
        if (timeoutMillis > 0) {
            ServerLogger.ROOT_LOGGER.suspendingServer(timeoutMillis);
        } else if (timeoutMillis < 0) {
            ServerLogger.ROOT_LOGGER.suspendingServerWithNoTimeout();
        } else {
            ServerLogger.ROOT_LOGGER.suspendingServer();
        }
        state = State.PRE_SUSPEND;
        //we iterate a copy, in case a listener tries to register a new listener
        for(OperationListener listener: new ArrayList<>(operationListeners)) {
            listener.suspendStarted();
        }
        outstandingCount = new AtomicInteger(activities.size());
        if (outstandingCount.get() == 0) {
            handlePause();
        } else {
            final ExecutorService executorService = this.executorService;
            CountingRequestCountCallback cb = new CountingRequestCountCallback(outstandingCount.get(), () -> {
                state = State.SUSPENDING;
                // Processing the deque with an {@link Iterator} means that {@link ServerActivity}
                // will be suspe nded with a LIFO logic
                for (ServerActivity activity : activities) {
                    executorService.submit(activity.suspended(this.listener));
                }
                return null;
            });

            // Processing the deque with an {@link Iterator} means that {@link ServerActivity}
            // will be pre-suspended with a LIFO logic
            for (ServerActivity activity : activities) {
                executorService.submit(activity.preSuspend(cb));
            }
            if (timeoutMillis > 0) {
                timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        timeout();
                    }
                }, timeoutMillis);
            } else if (timeoutMillis == 0) {
                timeout();
            }
        }
    }

    public void nonGracefulStart() {
        resume(false);
    }

    public void resume() {
        resume(true);
    }

    private synchronized void resume(boolean gracefulStart) {
        if (state == State.RUNNING) {
            return;
        }
        if (!gracefulStart) {
            ServerLogger.ROOT_LOGGER.startingNonGraceful();
        } else {
            ServerLogger.ROOT_LOGGER.resumingServer();
        }

        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        for(OperationListener listener: new ArrayList<>(operationListeners)) {
            listener.cancelled();
        }
        // Processing the deque with a descendingIterator means that {@link ServerActivity}
        // will be resumed with a FIFO logic
        Iterator<ServerActivity> reverseOrderIterator = activities.descendingIterator();
        while (reverseOrderIterator.hasNext()) {
            ServerActivity nextActivity = reverseOrderIterator.next();
            try {
                nextActivity.resume();
            } catch (Exception e) {
                ServerLogger.ROOT_LOGGER.failedToResume(nextActivity, e);
            }
        }
        state = State.RUNNING;
    }

    public synchronized void registerActivity(final ServerActivity activity) {
        this.activities.push(activity);
        if(state != State.RUNNING) {
            //if the activity is added when we are not running we just immediately suspend it
            //this should only happen at boot, so there should be no outstanding requests anyway
            activity.suspended(() -> null);
        }
    }

    public synchronized void unRegisterActivity(final ServerActivity activity) {
        this.activities.remove(activity);
    }

    @Override
    public synchronized void start(StartContext startContext) throws StartException {
        if(startSuspended) {
            ServerLogger.AS_ROOT_LOGGER.startingServerSuspended();
        }
    }

    @Override
    public synchronized void stop(StopContext stopContext) {
    }

    public State getState() {
        return state;
    }

    private synchronized Void activityPaused() {
        outstandingCount.decrementAndGet();
        handlePause();

        return null;
    }

    private void handlePause() {
        if (outstandingCount.get() == 0) {
            state = State.SUSPENDED;
            if (timer != null) {
                timer.cancel();
                timer = null;
            }

            for(OperationListener listener: new ArrayList<>(operationListeners)) {
                listener.complete();
            }
        }
    }

    private synchronized void timeout() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        for(OperationListener listener: new ArrayList<>(operationListeners)) {
            listener.timeout();
        }
    }


    public synchronized void addListener(final OperationListener listener) {
        operationListeners.add(listener);
    }

    public synchronized void removeListener(final OperationListener listener) {
        operationListeners.remove(listener);
    }

    @Override
    public SuspendController getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public InjectedValue<NotificationHandlerRegistry> getNotificationHandlerRegistry() {
        return notificationHandlerRegistry;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public enum State {
        RUNNING,
        PRE_SUSPEND,
        SUSPENDING,
        SUSPENDED
    }
}
