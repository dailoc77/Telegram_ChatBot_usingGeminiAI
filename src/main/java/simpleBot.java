import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;


public class simpleBot extends TelegramLongPollingBot {

    private static final String GEMINI_API_KEY = "AIzaSyB5jUwRl-1xTGHYIReFWSPwjXmHL-iVccM";
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=AIzaSyB5jUwRl-1xTGHYIReFWSPwjXmHL-iVccM";
    private final OkHttpClient client = new OkHttpClient();
    private final Map<String, Boolean> waitingForAlarmTime = new HashMap<>();

    @Override
    public String getBotUsername() {
        return "trdloc_bot";
    }

    @Override
    public String getBotToken() {
        return "6826919173:AAGPOsu8OaX3i9UFHnbFYII_RczWOFZmE3c";
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String userMessage = update.getMessage().getText();
            String chatId = update.getMessage().getChatId().toString();

            try {
                if (userMessage.equals("/alarm")) {
                    // Người dùng chọn lệnh /alarm
                    waitingForAlarmTime.put(chatId, true);

                    SendMessage message = new SendMessage();
                    message.setChatId(chatId);
                    message.setText("Hãy nhập thời gian báo thức theo định dạng HH:mm (ví dụ: 14:30)");
                    execute(message);

                } else if (waitingForAlarmTime.getOrDefault(chatId, false)) {
                    // Người dùng đang nhập thời gian báo thức
                    waitingForAlarmTime.remove(chatId); // Đã nhận thời gian => xoá trạng thái đợi

                    LocalTime alarmTime;
                    try {
                        alarmTime = LocalTime.parse(userMessage, DateTimeFormatter.ofPattern("HH:mm"));
                    } catch (Exception e) {
                        // Nếu nhập sai định dạng
                        SendMessage error = new SendMessage();
                        error.setChatId(chatId);
                        error.setText("Định dạng thời gian không hợp lệ! Vui lòng nhập theo dạng HH:mm (ví dụ: 14:30)");
                        execute(error);
                        return;
                    }

                    scheduleAlarm(chatId, alarmTime);

                    SendMessage confirm = new SendMessage();
                    confirm.setChatId(chatId);
                    confirm.setText("Báo thức đã được đặt lúc " + alarmTime.toString());
                    execute(confirm);

                } else {
                    // Xử lý các tin nhắn khác (ví dụ hỏi Gemini API)
                    String aiResponse = callGeminiAPI(userMessage);
                    if (aiResponse == null || aiResponse.isBlank()) {
                        aiResponse = "Xin lỗi, tôi chưa hiểu yêu cầu của bạn.";
                    }

                    // Kiểm tra độ dài của phản hồi AI và gửi
                    sendTelegramMessage(chatId, aiResponse);
                }
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    execute(SendMessage.builder()
                            .chatId(chatId)
                            .text("Có lỗi xảy ra, vui lòng thử lại sau!")
                            .build());
                } catch (TelegramApiException telegramApiException) {
                    telegramApiException.printStackTrace();
                }
            }
        }
    }


    private String callGeminiAPI(String userMessage) throws IOException {
        // Giới hạn độ dài tin nhắn nếu cần (ví dụ: 500 ký tự)
        int maxLength = 1000;
        if (userMessage.length() > maxLength) {
            userMessage = userMessage.substring(0, maxLength);  // Cắt tin nhắn nếu dài quá
        }

        String jsonRequest = "{\n" +
                "  \"contents\": [\n" +
                "    {\n" +
                "      \"parts\": [\n" +
                "        {\n" +
                "          \"text\": \"" + userMessage.replace("\"", "\\\"") + "\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        RequestBody body = RequestBody.create(jsonRequest, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(GEMINI_API_URL)  // Sử dụng URL của bạn
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.out.println("Gemini API lỗi: " + response);
                return null;
            }
            String responseBody = response.body().string();
            return extractTextFromGeminiResponse(responseBody);
        }
    }


    private String extractTextFromGeminiResponse(String responseBody) {
        try {
            // Sử dụng JSONObject để parse chuỗi JSON
            JSONObject jsonObject = new JSONObject(responseBody);
            JSONArray candidates = jsonObject.getJSONArray("candidates");

            if (candidates != null && candidates.length() > 0) {
                JSONObject firstCandidate = candidates.getJSONObject(0);
                JSONObject content = firstCandidate.getJSONObject("content");
                JSONArray parts = content.getJSONArray("parts");

                if (parts != null && parts.length() > 0) {
                    JSONObject part = parts.getJSONObject(0);
                    return part.getString("text");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;  // Nếu không tìm thấy giá trị "text", trả về null
    }

    private void sendTelegramMessage(String chatId, String message) throws TelegramApiException {
        int maxMessageLength = 4096;

        while (message.length() > maxMessageLength) {
            // Cắt một phần của tin nhắn và gửi
            String part = message.substring(0, maxMessageLength);
            sendSingleTelegramMessage(chatId, part);
            message = message.substring(maxMessageLength);
        }

        // Gửi phần còn lại
        if (!message.isEmpty()) {
            sendSingleTelegramMessage(chatId, message);
        }
    }

    private void sendSingleTelegramMessage(String chatId, String message) throws TelegramApiException {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message);
        sendMessage.setParseMode("Markdown");
        execute(sendMessage);
    }

    private void scheduleAlarm(String chatId, LocalTime alarmTime) {
        LocalTime now = LocalTime.now();
        long delayInSeconds = now.until(alarmTime, java.time.temporal.ChronoUnit.SECONDS);

        if (delayInSeconds < 0) {
            delayInSeconds += 24 * 60 * 60; // Nếu giờ đã qua hôm nay thì hẹn sang ngày mai
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            try {
                SendMessage alarmMessage = new SendMessage();
                alarmMessage.setChatId(chatId);
                alarmMessage.setText("⏰ Báo thức! Đã đến giờ bạn hẹn.");
                execute(alarmMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }, delayInSeconds, TimeUnit.SECONDS);
    }
}
