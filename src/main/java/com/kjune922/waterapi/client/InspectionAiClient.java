package com.kjune922.waterapi.client;

import com.kjune922.waterapi.dto.InspectionAnalysisResponse;

public interface InspectionAiClient {

    InspectionAnalysisResponse analyze(String inspectionContent);
}
