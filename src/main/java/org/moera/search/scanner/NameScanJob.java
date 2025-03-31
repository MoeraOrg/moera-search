package org.moera.search.scanner;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.WhoAmI;
import org.moera.search.api.NodeApi;
import org.moera.search.data.NameRepository;
import org.moera.search.job.Job;

public class NameScanJob extends Job<NameScanJob.Parameters, NameScanJob.State> {

    public static class Parameters {

        private String nodeName;

        public Parameters() {
        }

        public Parameters(String nodeName) {
            this.nodeName = nodeName;
        }

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

    }

    public static class State {

        private WhoAmI whoAmI;

        public State() {
        }

        public WhoAmI getWhoAmI() {
            return whoAmI;
        }

        public void setWhoAmI(WhoAmI whoAmI) {
            this.whoAmI = whoAmI;
        }

    }

    @Inject
    private NodeApi nodeApi;

    @Inject
    private NameRepository nameRepository;

    public NameScanJob() {
        state = new State();
        retryCount(3, "PT5M");
    }

    @Override
    protected void setParameters(String parameters, ObjectMapper objectMapper) throws JsonProcessingException {
        this.parameters = objectMapper.readValue(parameters, Parameters.class);
    }

    @Override
    protected void setState(String state, ObjectMapper objectMapper) throws JsonProcessingException {
        this.state = objectMapper.readValue(state, State.class);
    }

    @Override
    protected void execute() throws Exception {
        if (state.whoAmI == null) {
            state.whoAmI = nodeApi.at(parameters.nodeName).whoAmI();
            checkpoint();
        }

        database.executeWriteWithoutResult(() -> nameRepository.updateName(parameters.nodeName, state.whoAmI));
    }

    @Override
    protected void failed() {
        super.failed();
        database.executeWriteWithoutResult(() -> nameRepository.scanFailed(parameters.nodeName));
    }

}
