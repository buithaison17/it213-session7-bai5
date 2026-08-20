package com.example.bai5.service;

import com.example.bai5.dto.ChatbotResponse;
import com.example.bai5.dto.TicketDto;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketRagService {
    private static final double SIMILARITY_THRESHOLD = 0.6;
    private static final int TOP_K = 3;
    private static final String SYSTEM_PROMPT_TEMPLATE =
            "Bạn là Chuyên viên CSKH Cao cấp của tập đoàn bán lẻ công nghệ.\n" +
                    "Dưới đây là các phương án xử lý từ các ticket tương tự trong quá khứ:\n" +
                    "---------------------\n" +
                    "{context}\n" +
                    "---------------------\n" +
                    "Khiếu nại mới của khách hàng:\n" +
                    "\"\"\"{newComplaint}\"\"\"\n\n" +
                    "YÊU CẦU:\n" +
                    "1. Soạn thảo một bức thư trả lời gợi ý (Draft Response) gửi khách hàng với phong cách lịch sự, thấu hiểu.\n" +
                    "2. Chỉ sử dụng thông tin và giải pháp nghiệp vụ có trong phần ngữ cảnh trên.\n" +
                    "3. Tuyệt đối không tự bịa ra chính sách hoàn tiền, voucher hoặc cam kết chưa được ghi nhận.\n";
    private final PgVectorStore vectorStore;
    private final ChatClient chatClient;

    @Transactional(readOnly = true)
    public ChatbotResponse processComplaint(String newComplaint) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(newComplaint)
                .topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .build();
        List<Document> documents = vectorStore.similaritySearch(searchRequest);
        // 2. Logic kiểm duyệt dữ liệu phòng thủ (Defensive Validation)
        if (documents == null || documents.isEmpty()) {
            String fallbackEmail =
                    "Kính gửi Quý khách,\n\n" +
                            "Chúng tôi đã tiếp nhận yêu cầu khiếu nại của Quý khách. Do vấn đề cần được kiểm tra chuyên sâu theo quy trình kỹ thuật đặc thù, hệ thống đã chuyển tiếp thông tin này đến Chuyên viên CSKH Cấp cao để trực tiếp xử lý.\n" +
                            "Chúng tôi sẽ phản hồi lại Quý khách trong thời gian sớm nhất.\n\n" +
                            "Trân trọng,\n" +
                            "Bộ phận Chăm sóc Khách hàng.";
            return new ChatbotResponse(fallbackEmail, Collections.emptyList());
        }
        String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        List<TicketDto> references = documents.stream()
                .map(doc -> new TicketDto(
                        (String) doc.getMetadata().getOrDefault("ticketId", "N/A"),
                        (String) doc.getMetadata().getOrDefault("issue", doc.getText()),
                        (String) doc.getMetadata().getOrDefault("resolution", "Đã xử lý"),
                        (Double) doc.getMetadata().getOrDefault("distance", 1.0)
                ))
                .toList();

        String response = chatClient
                .prompt()
                .system(s -> s.text(SYSTEM_PROMPT_TEMPLATE).param("context", context))
                .user(context)
                .call()
                .content();

        return new ChatbotResponse(response, references);
    }
}
