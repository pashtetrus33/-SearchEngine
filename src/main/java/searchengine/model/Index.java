package searchengine.model;

import lombok.*;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "`index`")
public class Index {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false, referencedColumnName = "id")
    private WebPage page;

    @ManyToOne
    @JoinColumn(name = "lemma_id", nullable = false, referencedColumnName = "id")
    private Lemma lemma;

    @Column(name = "`rank`", nullable = false)
    private Float rank;
}
