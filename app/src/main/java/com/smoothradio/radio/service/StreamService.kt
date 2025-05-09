package com.smoothradio.radio.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.smoothradio.radio.MainActivity;
import com.smoothradio.radio.R;

/**
 * A background service that manages audio streaming using ExoPlayer.
 *
 * This service handles playback, pause/resume, stop, audio focus management,
 * notifications, and broadcast communication with the UI.  It supports playing
 * audio streams from URLs and responds to various actions triggered by the UI or system events.
 */
public class StreamService extends Service {

    // Broadcast Actions
    public static final String ACTION_START = "SmoothService:Start";
    public static final String ACTION_STOP = "SmoothService:Stop";
    public static final String ACTION_SHOW_AD = "SmoothService:Stop";
    public static final String ACTION_EVENT_CHANGE = "SmoothService:EventChangeListener";
    public static final String ACTION_METADATA_CHANGE = "SmoothService:MetadataChangeListener";
    public static final String ACTION_GET_STATE = "SmoothService:GetState";
    public static final String ACTION_UPDATE_UI = "SmoothService:ReturnState";
    public static final String ACTION_SET_TIMER = "SmoothService:SetTimer";
    private final String ACTION_PLAY_PAUSE = "SmoothService:PlayPause";
    private final String ACTION_REQUEST_AUDIO_FOCUS = "SmoothService:RequestAudioFocus";

    // Broadcast Extras
    public static final String EXTRA_RESULT_DATA = "SmoothService:ShowAd";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_TIME_IN_MILLIS = "timeInMillis";
    public static final String EXTRA_LOGO = "logo";
    public static final String EXTRA_STATION_NAME = "stationName";
    public static final String EXTRA_LINK = "url";
    public static final String EXTRA_TITLE = "title";
    public final String EXTRA_SOURCE = "source";

    // Notification Info
    private static final String CHANNEL_ID = "serviceChannel";
    private final String TITLE_PLAY = "Play";
    private final String TITLE_PAUSE = "Pause";
    private final String TITLE_STOP = "Stop";

    // Playback State
    public static boolean isPlaying = false;
    private boolean isShowingAd = false;
    private String stateChange = "";

    // Playback Components
    private ExoPlayer player;
    private MediaMetadata mediaMetadataOR;
    private EventListener exoplayerEventListener;

    // System Services
    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener;

    // Broadcast Intents
    private Intent eventIntent;
    private Intent metadataIntent;
    private Intent playPauseIntent;

    // Notification Components
    private NotificationCompat.Builder notificationBuilder;
    private PendingIntent stopPI;
    private PendingIntent playPausePI;

    // Broadcast Receivers
    private StopPlayFromTimerReceiver stopPlayFromTimerReceiver;
    private SetStopTimerReceiver setStopTimerReceiver;
    private RestoreUIReceiver restoreUIReceiver;
    private PLayPauseReceiver pLayPauseReceiver;
    private RequestFocusReceiver requestFocusReceiver;

    // === Lifecycle Methods ===
    @Override
    public void onCreate() {
        super.onCreate();
        setupAudioFocus();
        setupIntents();
        setupNotification();
        registerReceivers();
        exoplayerEventListener = new EventListener();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            handleIntent(intent);
        }
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanupResources();
    }

    private void setupAudioFocus() {
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        playPauseIntent = new Intent();
        onAudioFocusChangeListener = i -> {
            if (i == AudioManager.AUDIOFOCUS_GAIN) {
                player.play();
            } else if (i == AudioManager.AUDIOFOCUS_LOSS || i == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                playPauseIntent.putExtra(EXTRA_SOURCE, "audiofocus");
                sendBroadcast(playPauseIntent);
            }
        };
    }

    private void setupIntents() {
        eventIntent = new Intent(ACTION_EVENT_CHANGE).setPackage(getPackageName());
        metadataIntent = new Intent(ACTION_METADATA_CHANGE).setPackage(getPackageName());
    }

    private void setupNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setSound(null, null);
            channel.setShowBadge(false);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
        Intent notificationIntent = new Intent(this, MainActivity.class).setPackage(getPackageName());
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(ACTION_STOP).setPackage(getPackageName());
        stopPI = PendingIntent.getBroadcast(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        playPauseIntent.setAction(ACTION_PLAY_PAUSE).setPackage(getPackageName());
        playPausePI = PendingIntent.getBroadcast(this, 1, playPauseIntent, PendingIntent.FLAG_IMMUTABLE);

        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.notificationicon)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(R.drawable.exo_notification_play, TITLE_PAUSE, playPausePI)
                .addAction(R.drawable.exo_notification_stop, TITLE_STOP, stopPI)
                .setColor(ContextCompat.getColor(this, R.color.red))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1));
    }

    private void registerReceivers() {
        stopPlayFromTimerReceiver = new StopPlayFromTimerReceiver();
        setStopTimerReceiver = new SetStopTimerReceiver();
        restoreUIReceiver = new RestoreUIReceiver();
        pLayPauseReceiver = new PLayPauseReceiver();
        requestFocusReceiver = new RequestFocusReceiver();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopPlayFromTimerReceiver, new IntentFilter(ACTION_STOP), RECEIVER_NOT_EXPORTED);
            registerReceiver(restoreUIReceiver, new IntentFilter(ACTION_GET_STATE), RECEIVER_NOT_EXPORTED);
            registerReceiver(setStopTimerReceiver, new IntentFilter(ACTION_SET_TIMER), RECEIVER_NOT_EXPORTED);
            registerReceiver(pLayPauseReceiver, new IntentFilter(ACTION_PLAY_PAUSE), RECEIVER_NOT_EXPORTED);
            registerReceiver(requestFocusReceiver, new IntentFilter(ACTION_REQUEST_AUDIO_FOCUS), RECEIVER_NOT_EXPORTED);
            return;
        }
        registerReceiver(stopPlayFromTimerReceiver, new IntentFilter(ACTION_STOP));
        registerReceiver(restoreUIReceiver, new IntentFilter(ACTION_GET_STATE));
        registerReceiver(setStopTimerReceiver, new IntentFilter(ACTION_SET_TIMER));
        registerReceiver(pLayPauseReceiver, new IntentFilter(ACTION_PLAY_PAUSE));
        registerReceiver(requestFocusReceiver, new IntentFilter(ACTION_REQUEST_AUDIO_FOCUS));
    }

    private void cleanupResources() {
        audioManager.abandonAudioFocus(onAudioFocusChangeListener);
        if (player != null) {
            player.stop();
            player.removeListener(exoplayerEventListener);
            player.release();
        }
        isPlaying = false;
        stateChange = "";
        broadcastState(stateChange);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
        unregisterAllReceivers();
    }

    private void unregisterAllReceivers() {
        unregisterReceiver(stopPlayFromTimerReceiver);
        unregisterReceiver(setStopTimerReceiver);
        unregisterReceiver(restoreUIReceiver);
        unregisterReceiver(pLayPauseReceiver);
        unregisterReceiver(requestFocusReceiver);
    }

    private void handleIntent(Intent intent) {
        String link = intent.getStringExtra(EXTRA_LINK);
        if (link == null) link = ""; //avoid crash if backend has no value

        int logo = intent.getIntExtra(EXTRA_LOGO, 0);
        String stationName = intent.getStringExtra(EXTRA_STATION_NAME);

        notificationBuilder.setContentTitle(stationName)
                .setContentText(stateChange)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), logo));
        startForeground(1, notificationBuilder.build());

        if (ACTION_START.equals(intent.getAction())) {
            play(link);
        } else if (ACTION_SHOW_AD.equals(intent.getAction())) {
            prepareShowAd();
        } else {
            throw new IllegalArgumentException("Unexpected action received: " + intent.getAction());
        }
    }

    private void play(String link) {
        stateChange = StreamStates.PREPARING;
        updateNotification(stateChange,false,false);
        preparePlayer(Uri.parse(link));
        requestFocus();
        player.play();
    }

    private void preparePlayer(Uri uri) {
        if (player != null) {
            player.release();
        }
        MediaItem mediaItem = MediaItem.fromUri(uri);
        player = new ExoPlayer.Builder(this).build();
        player.setMediaItem(mediaItem);
        player.prepare();
        player.addListener(exoplayerEventListener);
    }

    private void prepareShowAd() {
        abandonFocus();
        if (player != null) {
            player.stop();
            player.release();
        }
        isPlaying = false;
        stateChange = StreamStates.PREPARING;
        broadcastState(stateChange);
    }
    private void broadcastState(String state) {
        eventIntent.putExtra(EXTRA_STATE, state);
        sendBroadcast(eventIntent);
    }

    private void updateNotification(String contentText, boolean showPlay, boolean showPause) {
        notificationBuilder.setContentText(contentText);
        notificationBuilder.mActions.clear();
        if (showPlay) {
            notificationBuilder.addAction(R.drawable.exo_notification_play, TITLE_PLAY, playPausePI);
        }

        if (showPause) {
            notificationBuilder.addAction(R.drawable.exo_notification_pause, TITLE_PAUSE, playPausePI);
        }
        notificationBuilder.addAction(R.drawable.exo_notification_stop, TITLE_STOP, stopPI);
        androidx.media.app.NotificationCompat.MediaStyle mediaStyle = new androidx.media.app.NotificationCompat.MediaStyle();

        if (!(showPlay || showPause)) {
            mediaStyle.setShowActionsInCompactView(0);
        } else {
            mediaStyle.setShowActionsInCompactView(0, 1);
        }

        notificationBuilder.setStyle(mediaStyle);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1, notificationBuilder.build());
    }
    private void requestFocus() {
        audioManager.requestAudioFocus(onAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    private void abandonFocus() {
        audioManager.abandonAudioFocus(onAudioFocusChangeListener);
    }


    class PLayPauseReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String source = intent.getStringExtra(EXTRA_SOURCE);
            if (isPlaying) {
                pausePlayer(source);
            } else {
                resumePlayer();
                mediaMetadataOR = player.getMediaMetadata();
                getSendMetadata(mediaMetadataOR);
            }
        }
    }
    void getSendMetadata(MediaMetadata mediaMetadata) {
        if (mediaMetadata.title != null && isPlaying) {
            String title = (String) mediaMetadata.title;
            metadataIntent.putExtra(EXTRA_TITLE, title);
            sendBroadcast(metadataIntent);
        }
    }

    private void pausePlayer(String source) {
        player.pause();
        if (source == null) abandonFocus();
        isPlaying = false;
        stateChange = StreamStates.IDLE;
        broadcastState(stateChange);
        updateNotification(getString(R.string.paused), true, false);
    }

    private void resumePlayer() {
        requestFocus();
        player.play();
        isPlaying = true;
        stateChange = StreamStates.PLAYING;
        broadcastState(stateChange);
        updateNotification(stateChange, false, true);
    }


    class StopPlayFromTimerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(context, getString(R.string.stopped), Toast.LENGTH_SHORT).show();
            stopSelf();
        }
    }

    class SetStopTimerReceiver extends BroadcastReceiver {
        Intent stopPlayFromTimerIntent = new Intent(ACTION_STOP).setPackage(getPackageName());

        @Override
        public void onReceive(Context context, Intent intent) {
            long timeInMillis = intent.getLongExtra(EXTRA_TIME_IN_MILLIS, 0);
            PendingIntent alarmPendingIntent = PendingIntent.getBroadcast(StreamService.this, 0, stopPlayFromTimerIntent, PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeInMillis, alarmPendingIntent);
        }
    }

    class RequestFocusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            player.play();
            requestFocus();
        }
    }

    class RestoreUIReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (player != null) {
                eventIntent.putExtra(EXTRA_STATE, stateChange);
                sendBroadcast(eventIntent);
                mediaMetadataOR = player.getMediaMetadata();
                getSendMetadata(mediaMetadataOR);
            }
        }
    }

    class EventListener implements Player.Listener {
        @Override
        public void onMediaMetadataChanged(@NonNull MediaMetadata mediaMetadata) {
            getSendMetadata(mediaMetadata);
        }
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (isPlaying) {
                requestFocus();
                stateChange = StreamStates.PLAYING;
                StreamService.isPlaying = true;
                broadcastState(stateChange);
                updateNotification(stateChange, false, true);
            }

        }

        @Override
        public void onPlayerError(PlaybackException error) {
            String message = (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                    || error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                    || error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
                    ? getString(R.string.toast_station_unreachable) : getString(R.string.toast_unexpected_error);
            Toast.makeText(StreamService.this, message, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_BUFFERING) {
                handleBufferingState();
            } else if (state == Player.STATE_IDLE) {
                handleIdleState();
            } else if (state == Player.STATE_READY) {
                handleReadyState();
            } else if (state == Player.STATE_ENDED) {
                handleEndedState();
            }
        }
        private void handleBufferingState() {
            abandonFocus();
            stateChange = StreamStates.BUFFERING;
            broadcastState(stateChange);
            updateNotification(stateChange, false, false);
        }

        private void handleIdleState() {
            abandonFocus();
            stateChange = StreamStates.IDLE;
            isPlaying = false;
            broadcastState(stateChange);
            updateNotification(stateChange, true, false);
        }

        private void handleReadyState() {
            requestFocus();
            stateChange = StreamStates.PLAYING;
            isPlaying = true;
            broadcastState(stateChange);
            updateNotification(stateChange, false, true);
        }

        private void handleEndedState() {
            abandonFocus();
            stateChange = StreamStates.ENDED;
            isPlaying = false;
            broadcastState(stateChange);
            updateNotification(stateChange, true, false);
        }

    }
    public static final class StreamStates {
        public static final String PREPARING = "Preparing Audio";
        public static final String PLAYING = "Playing";
        public static final String BUFFERING = "Buffering";
        public static final String IDLE = "Idle";
        public static final String ENDED = "Ended";
    }
}