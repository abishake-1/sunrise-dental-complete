package com.sunrisedental.pattern;

/**
 * A message to deliver, independent of any provider.
 *
 * @param notificationId   id of the row in the {@code notifications} log
 * @param channel          EMAIL, SMS or IN_APP
 * @param recipientName    recipient's name
 * @param recipientAddress e-mail address or phone number, depending on the channel
 * @param subject          short subject (used by e-mail)
 * @param body             message text
 */
public record NotificationMessage(long notificationId, String channel, String recipientName,
                                  String recipientAddress, String subject, String body) {

    /** Rejects incomplete messages. */
    public NotificationMessage {
        if (channel == null || channel.isBlank() || recipientAddress == null || recipientAddress.isBlank()
                || body == null || body.isBlank()) {
            throw new IllegalArgumentException("channel, recipientAddress and body are required");
        }
    }
}
