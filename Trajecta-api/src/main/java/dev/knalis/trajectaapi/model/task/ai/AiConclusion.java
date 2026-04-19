package dev.knalis.trajectaapi.model.task.ai;

import dev.knalis.trajectaapi.model.task.FlightTask;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class AiConclusion {
    
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;
    
    @Enumerated(EnumType.STRING)
    private AiModel aiModel = AiModel.GPT_4O_MINI;
    
    @Column(length=5000)
    private String conclusion;
    
    @OneToOne
    private FlightTask flightTask;
    
    private String errorMessage;
    
}
