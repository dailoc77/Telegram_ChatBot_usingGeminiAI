import com.fasterxml.jackson.core.JsonParser;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;


public class simpleBot extends TelegramLongPollingBot {

    private static final String GEMINI_API_KEY = "AIzaSyB5jUwRl-1xTGHYIReFWSPwjXmHL-iVccM";
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=AIzaSyB5jUwRl-1xTGHYIReFWSPwjXmHL-iVccM";
    private final OkHttpClient client = new OkHttpClient();

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
                String aiResponse = callGeminiAPI(userMessage);
                if (aiResponse == null || aiResponse.isBlank()) {
                    aiResponse = "Xin lỗi, tôi chưa hiểu yêu cầu của bạn.";
                }

                // Kiểm tra độ dài của phản hồi AI và gửi
                sendTelegramMessage(chatId, aiResponse);
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



}
