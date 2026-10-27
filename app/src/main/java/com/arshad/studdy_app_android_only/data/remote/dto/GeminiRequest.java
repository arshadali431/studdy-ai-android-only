package com.arshad.studdy_app_android_only.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * Request body for Google Gemini API generateContent endpoint.
 */
public class GeminiRequest {

    @SerializedName("contents")
    public List<Content> contents;

    @SerializedName("generationConfig")
    public GenerationConfig generationConfig;

    public GeminiRequest(String prompt) {
        this(prompt, null);
    }

    public GeminiRequest(String prompt, String pdfBase64) {
        this.contents = new ArrayList<>();
        Content content = new Content();
        content.parts = new ArrayList<>();

        if (pdfBase64 != null && !pdfBase64.isEmpty()) {
            Part filePart = new Part();
            filePart.inlineData = new InlineData("application/pdf", pdfBase64);
            content.parts.add(filePart);
        }

        Part textPart = new Part();
        textPart.text = prompt;
        content.parts.add(textPart);
        this.contents.add(content);

        this.generationConfig = new GenerationConfig();
        this.generationConfig.responseMimeType = "application/json";
    }

    public static class Content {
        @SerializedName("parts")
        public List<Part> parts;
    }

    public static class Part {
        @SerializedName("text")
        public String text;

        @SerializedName("inlineData")
        public InlineData inlineData;
    }

    public static class InlineData {
        @SerializedName("mimeType")
        public String mimeType;

        @SerializedName("data")
        public String data; // Base64

        public InlineData(String mimeType, String data) {
            this.mimeType = mimeType;
            this.data = data;
        }
    }

    public static class GenerationConfig {
        @SerializedName("responseMimeType")
        public String responseMimeType;
    }
}
