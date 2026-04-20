package varga.supportplane.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComponentSummaryResponse {

    private String component;
    private int okCount;
    private int warningCount;
    private int criticalCount;
    private int unknownCount;
}
