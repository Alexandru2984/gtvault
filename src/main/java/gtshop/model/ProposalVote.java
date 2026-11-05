package gtshop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "proposal_votes", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"proposal_id", "user_id"})
})
public class ProposalVote {

    @Id
    private String id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_id", nullable = false)
    private RewardProposal proposal;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserProfile user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ProposalVote() {
    }

    public ProposalVote(String id, RewardProposal proposal, UserProfile user, Instant createdAt) {
        this.id = id;
        this.proposal = proposal;
        this.user = user;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public RewardProposal getProposal() {
        return proposal;
    }

    public void setProposal(RewardProposal proposal) {
        this.proposal = proposal;
    }

    public UserProfile getUser() {
        return user;
    }

    public void setUser(UserProfile user) {
        this.user = user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
