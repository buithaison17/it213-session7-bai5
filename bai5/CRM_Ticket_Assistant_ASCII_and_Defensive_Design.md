# BÀI LÀM: SƠ ĐỒ LUỒNG DỮ LIỆU & GIẢI PHÁP THIẾT KẾ PHÒNG THỦ DỮ LIỆU
**Môn học:** AI Integration in Action  
**Case Study:** CRM Ticket Assistant (RAG Pipeline with Spring AI & pgvector)  
**Nội dung:** Sơ đồ ASCII & Phân tích giải pháp thiết kế phòng thủ dữ liệu

---

## 1. Sơ đồ luồng xử lý dữ liệu ASCII

```text
+-----------------------+
| CSKH Client / Frontend|
+-----------+-----------+
            |
            | 1. POST /api/v1/tickets/assist (newComplaint)
            v
+-----------+-------------------------------------------------------------+
| REST Controller: TicketAssistantController                             |
+-----------+-------------------------------------------------------------+
            |
            | 2. Gọi processComplaint(newComplaint)
            v
+-----------+-------------------------------------------------------------+
| Service Layer: TicketRagService (@Transactional(readOnly = true))       |
+-----------+-------------------------------------------------------------+
            |
            | 3. VectorStore.similaritySearch(
            |       SearchRequest.query(newComplaint).withTopK(3).withSimilarityThreshold(0.6)
            |    )
            v
+-----------+-----------+               +---------------------------------+
|  Embedding Client     | ------------> | PostgreSQL (pgvector)           |
| (Vector hóa câu query)|               | (Cosine Similarity Search)      |
+-----------------------+               +----------------+----------------+
                                                         |
                                                         | 4. Trả về List<Document>
                                                         v
                                        +----------------+----------------+
                                        | Defensive Validation Step       |
                                        | (matchedDocs.isEmpty() ?)       |
                                        +----------------+----------------+
                                                         |
                       +---------------------------------+---------------------------------+
                       |                                                                   |
                 [YES: Rỗng]                                                         [NO: Có dữ liệu]
                       |                                                                   |
                       v                                                                   v
     +-----------------------------------+                               +-----------------------------------+
     | Fallback / Default Response       |                               | 5. System Prompt Construction     |
     | - Skip calling LLM (Ngắt luồng    |                               | - Map Context & Metadatas         |
     |   để chống Hallucination & tối    |                               | - Strict Grounding Constraints    |
     |   ưu chi phí token API)           |                               +-----------------+-----------------+
     | - Gửi email xin lỗi + Chuyển tiếp |                                                 |
     |   thủ công cho chuyên viên cao cấp|                                                 | 6. Call ChatModel.call(prompt)
     | - references: List.of()           |                                                 v
     +-----------------+-----------------+                               +-----------------+-----------------+
                       |                                                 | ChatModel LLM (OpenAI / Ollama)   |
                       |                                                 | (Soạn Draft Response bám context) |
                       |                                                 +-----------------+-----------------+
                       |                                                                   |
                       |                                                                   | 7. Trả về email gợi ý
                       |                                                                   v
                       |                                                 +-----------------+-----------------+
                       |                                                 | Map DTO & Reference List          |
                       |                                                 +-----------------+-----------------+
                       |                                                                   |
                       +---------------------------------+---------------------------------+
                                                         |
                                                         | 8. Trả về ChatbotResponse
                                                         v
                                            +------------+------------+
                                            | JSON Response to Client |
                                            +-------------------------+
```

---

## 2. Giải pháp thiết kế phòng thủ dữ liệu (Defensive Data Design)

### 2.1. Kiểm duyệt ngữ cảnh trước khi gọi LLM (Pre-Invocation Guardrail / Short-circuiting)
- **Vấn đề triệt tiêu:** Lỗi nghiêm trọng nhất của các hệ sinh thái LLM trong CSKH là hiện tượng ảo tưởng (Hallucination) – tự hứa hẹn đền bù voucher, hoàn tiền hoặc đổi mới thiết bị khi không tìm thấy tài liệu phù hợp.
- **Cơ chế thực thi:** 
  - Đặt ngưỡng tương đồng tối thiểu `similarityThreshold = 0.6` và lấy `topK = 3`.
  - Kiểm tra điều kiện `matchedDocs.isEmpty()` ngay sau khi truy vấn từ PostgreSQL:
    - Nếu kết quả rỗng (không có ticket nào trong quá khứ tương đồng $\ge 0.6$), hệ thống lập tức ngắt luồng (short-circuit), trả về ngay phản hồi mặc định (Fallback Response) thông báo chuyển tiếp thủ công cho chuyên viên cao cấp.
  - **Lợi ích:** Triệt tiêu hoàn toàn nguy cơ ảo tưởng chính sách, đồng thời tiết kiệm 100% chi phí token API và giảm độ trễ (latency) xuống mức tối thiểu.

### 2.2. Định vị thông tin nghiêm ngặt (Strict Grounding & Role Isolation)
- **Cơ chế:** Khi có dữ liệu tương đồng, System Prompt được thiết lập chặt chẽ để cô lập không gian suy diễn của LLM.
- LLM chỉ được phép trích xuất các phương án giải quyết đã được ghi nhận trong `context`, nghiêm cấm tự suy diễn chính sách bồi thường vượt quá thẩm quyền.

### 2.3. Tối ưu hóa hiệu năng & cách ly giao dịch (`@Transactional(readOnly = true)`)
- Đánh dấu giao dịch ở mức Read-Only giúp Spring/Hibernate tối ưu hóa kết nối, tắt chế độ dirty checking trên snapshot bộ nhớ và giảm tải lock tài nguyên trong PostgreSQL, đảm bảo khả năng mở rộng (scalability) khi xử lý đồng thời hàng nghìn khiếu nại.
