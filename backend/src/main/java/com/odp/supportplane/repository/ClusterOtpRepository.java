package com.odp.supportplane.repository;

import com.odp.supportplane.model.ClusterOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ClusterOtpRepository extends JpaRepository<ClusterOtp, Long> {
    Optional<ClusterOtp> findByClusterIdAndOtpCodeAndUsedFalse(Long clusterId, String otpCode);
    Optional<ClusterOtp> findTopByClusterIdAndUsedFalseOrderByCreatedAtDesc(Long clusterId);
}
