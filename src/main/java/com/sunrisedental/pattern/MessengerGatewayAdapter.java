package com.sunrisedental.pattern;

import java.util.regex.Pattern;

/**
 * Adapter pattern, <em>adapter</em>: makes a {@link LegacyMessenger} usable as a {@link NotificationGateway}.
 * It translates our message object into the provider's strings (international phone format, subject folded
 * into the text for e-mail) and the provider's integer status code into an exception.
 */
public final class MessengerGatewayAdapter implements NotificationGateway {

    private static final Pattern LOCAL_MOBILE = Pattern.compile("^07\\d{8}$");

    private final LegacyMessenger messenger;

    /**
     * Creates the adapter.
     *
     * @param messenger the provider to adapt
     */
    public MessengerGatewayAdapter(LegacyMessenger messenger) {
        this.messenger = messenger;
    }

    @Override
    public String send(NotificationMessage message) throws NotificationException {
        String destination = "SMS".equals(message.channel())
                ? toInternational(message.recipientAddress()) : message.recipientAddress();
        String text = "EMAIL".equals(message.channel())
                ? "Subject: " + message.subject() + " | " + message.body() : message.body();
        int code = messenger.push(destination, text);
        if (code != LegacyMessenger.OK) {
            throw new NotificationException("Provider rejected the message (code " + code + ")");
        }
        return "MOCK-" + message.notificationId();
    }

    /** Converts 07XXXXXXXX to +947XXXXXXXX; anything else is passed through unchanged. */
    static String toInternational(String phone) {
        return LOCAL_MOBILE.matcher(phone).matches() ? "+94" + phone.substring(1) : phone;
    }
}
