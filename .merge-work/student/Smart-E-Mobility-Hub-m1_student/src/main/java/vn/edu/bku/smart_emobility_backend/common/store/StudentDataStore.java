package vn.edu.bku.smart_emobility_backend.common.store;

import java.util.Optional;
import vn.edu.bku.smart_emobility_backend.common.domain.Student;

public interface StudentDataStore {
    Optional<Student> findById(String studentId);
}
