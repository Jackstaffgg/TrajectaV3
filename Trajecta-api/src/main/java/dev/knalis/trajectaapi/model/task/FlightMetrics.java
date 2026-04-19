package dev.knalis.trajectaapi.model.task;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class FlightMetrics {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private Long taskId;
    
    private Double maxAltitude;
    private Double maxSpeed;
    private Double flightDuration;
    private Double distance;
    private Double maxVerticalSpeed;
    private Double endpointErrorM;
    private Double rmsCrosstrackErrorM;
    private Double ateM;
    private Double p95CrosstrackErrorM;
    private Integer pointCount;
    private Boolean groundTruthUsed;
}


