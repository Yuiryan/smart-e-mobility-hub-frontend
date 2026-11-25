package vn.edu.bku.smart_emobility_backend.common.store;

import java.util.List;
import vn.edu.bku.smart_emobility_backend.common.domain.MobilityHub;
import vn.edu.bku.smart_emobility_backend.common.domain.SearchCriteria;

public interface HubDataStore {
    List<MobilityHub> findNearby(SearchCriteria criteria);
}
