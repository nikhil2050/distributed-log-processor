package com.nikhil.logprocessor.consumer.repository;

import com.nikhil.logprocessor.consumer.model.LogEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LogEventRepository extends JpaRepository<LogEvent, String> {

}
