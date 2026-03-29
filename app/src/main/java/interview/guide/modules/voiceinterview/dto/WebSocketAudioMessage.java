package interview.guide.modules.voiceinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketAudioMessage {
    private String type; // "audio"
    private String data; // Base64 encoded audio
    private Long timestamp;
}
