package com.whatsapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Person;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class SendFixture extends BroadcastReceiver {
    private static final String CHANNEL = "synthetic-guardian-source";
    private static long firstMessageAt;
    private static long foldMessageAt;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"uk.co.traynor.privategallery.guardian.FIXTURE".equals(intent.getAction())) return;
        String scenario = intent.getStringExtra("scenario");
        if (scenario == null) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Synthetic Guardian messages", NotificationManager.IMPORTANCE_DEFAULT));
        long now = System.currentTimeMillis();
        Person self = new Person.Builder().setName("Fixture owner").setKey("self").build();
        Person sender = new Person.Builder().setName("Fixture sender").setKey("fixture-sender").build();
        Notification.MessagingStyle messages = new Notification.MessagingStyle(self);
        Notification.Builder notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setCategory(Notification.CATEGORY_MESSAGE);
        int id;
        switch (scenario) {
            case "hi":
                firstMessageAt = now - 5;
                id = 17;
                messages.addMessage("Hi", firstMessageAt, sender);
                break;
            case "hostile":
                if (firstMessageAt == 0L) return;
                id = 17;
                messages.addMessage("Hi", firstMessageAt, sender);
                messages.addMessage("You need to shut the fuck up. When I was 17.", firstMessageAt + 1, sender);
                break;
            case "summary":
                id = 30;
                notification.setGroup("synthetic-group").setGroupSummary(true);
                messages.addMessage("Summary must stay with the source", now, sender);
                break;
            case "morning":
                id = 18;
                notification.setGroup("synthetic-group");
                messages.addMessage("Good morning", now, sender);
                break;
            case "uncertain":
                id = 19;
                messages.addMessage("When I was 17.", now, sender);
                break;
            case "held-uncertain":
                id = 20;
                messages.addMessage("An unfinished example", now, sender);
                break;
            case "excluded":
                id = 21;
                messages.addMessage("Hello", now, sender);
                break;
            case "quiet":
                id = 22;
                messages.addMessage("Thank you", now, sender);
                break;
            case "blocked":
                id = 23;
                messages.addMessage("See you tomorrow", now, sender);
                break;
            case "background":
                id = 24;
                messages.addMessage("Good afternoon", now, sender);
                break;
            case "background-hostile":
                id = 25;
                messages.addMessage("You need to shut the fuck up. When I was 17.", now, sender);
                break;
            case "fold-hi":
                foldMessageAt = now - 5;
                id = 40;
                notification.setGroup("fold-guardian-group");
                messages.addMessage("Hi", foldMessageAt, sender);
                break;
            case "fold-three":
                if (foldMessageAt == 0L) return;
                id = 40;
                notification.setGroup("fold-guardian-group");
                messages.addMessage("Hi", foldMessageAt, sender);
                messages.addMessage("When I was 17.", foldMessageAt + 1, sender);
                messages.addMessage("Fuck you", foldMessageAt + 2, sender);
                break;
            case "fold-summary":
                manager.notify("guardian93-source", 39, notification.setGroup("fold-guardian-group")
                        .setGroupSummary(true).setContentTitle("Fixture message count").build());
                return;
            case "fold-uncertain":
                id = 41;
                messages.addMessage("When I was 17.", now, sender);
                break;
            case "fold-safe":
                id = 42;
                notification.setGroup("fold-guardian-group");
                messages.addMessage("My earlier reply.", now - 2, (Person) null);
                messages.addMessage("I will call later.", now - 1, self);
                messages.addMessage("Good afternoon", now, sender);
                break;
            case "fold-smart-greeting":
                id = 43;
                messages.addMessage("Good evening", now, sender);
                break;
            case "fold-smart-hostile":
                if (foldMessageAt == 0L) return;
                id = 40;
                notification.setGroup("fold-guardian-group");
                messages.addMessage("Hi", foldMessageAt, sender);
                messages.addMessage("When I was 17.", foldMessageAt + 1, sender);
                messages.addMessage("Fuck you", foldMessageAt + 2, sender);
                messages.addMessage("Fuck you!", now, sender);
                break;
            default:
                return;
        }
        notification.setShortcutId("fixture-" + id);
        manager.notify("guardian93-source", id, notification.setStyle(messages).build());
    }
}
