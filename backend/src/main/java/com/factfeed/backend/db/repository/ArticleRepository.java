package com.factfeed.backend.db.repository;

import com.factfeed.backend.db.entity.Article;
import com.factfeed.backend.scraper.model.NewsSource;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
    @Query("SELECT a.url FROM Article a WHERE a.source = :source ORDER BY a.articlePublishedAt DESC LIMIT 10")
    List<String> findRecentUrlsBySource(@Param("source") NewsSource source);
}