package com.sharenote.redistribution.repository.shard2;

import com.sharenote.redistribution.entity.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface Shard2PageRepository extends JpaRepository<Page, UUID> {

}