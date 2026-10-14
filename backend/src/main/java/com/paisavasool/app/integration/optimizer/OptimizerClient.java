package com.paisavasool.app.integration.optimizer;

import com.paisavasool.app.integration.optimizer.dto.OptimizerRequest;
import com.paisavasool.app.integration.optimizer.dto.OptimizerResponse;

public interface OptimizerClient {

    OptimizerResponse optimize(OptimizerRequest request);
}
