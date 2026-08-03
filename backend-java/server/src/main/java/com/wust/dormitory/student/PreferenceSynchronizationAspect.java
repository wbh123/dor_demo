package com.wust.dormitory.student;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.security.CurrentUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Aspect @Component
public class PreferenceSynchronizationAspect {
    private final StudentPreferenceService preferences; private final NamedParameterJdbcTemplate jdbc; private final ObjectMapper mapper;
    public PreferenceSynchronizationAspect(StudentPreferenceService preferences,NamedParameterJdbcTemplate jdbc,ObjectMapper mapper){this.preferences=preferences;this.jdbc=jdbc;this.mapper=mapper;}
    @AfterReturning("execution(* com.wust.dormitory.student.StudentService.submitQuestionnaire(..)) && args(batchId,answers,user)")
    public void sync(long batchId,Map<String,Object> answers,CurrentUser user){
        List<String> vectors=jdbc.query("SELECT feature_vector_json FROM student_feature WHERE batch_id=:batchId AND student_id=:studentId",Map.of("batchId",batchId,"studentId",user.studentId()),(rs,n)->rs.getString(1));
        if(vectors.isEmpty()||vectors.getFirst()==null)return;
        try{preferences.synchronizeFromBatch(batchId,user.studentId(),answers,mapper.readValue(vectors.getFirst(),new TypeReference<Map<String,Object>>(){}));}catch(Exception ignored){}
    }
    @AfterReturning(pointcut="execution(* com.wust.dormitory.student.StudentService.batches(..)) && args(user)",returning="batches")
    public void batches(CurrentUser user,List<Map<String,Object>> batches){if(preferences.completed(user.studentId()))batches.forEach(b->b.put("questionnaire_started",true));}
    @Around("execution(* com.wust.dormitory.student.StudentService.questionnaire(..)) && args(batchId,user)")
    public Object questionnaire(ProceedingJoinPoint jp,long batchId,CurrentUser user)throws Throwable{
        Object value=jp.proceed(); if(!(value instanceof Map<?,?> source))return value;
        Map<String,Object> result=new LinkedHashMap<>();source.forEach((k,v)->result.put(String.valueOf(k),v));
        Object answers=result.get("answers");boolean empty=answers instanceof List<?> l&&l.isEmpty();
        result.put("profileAnswers",empty?preferences.storedAnswers(user.studentId()):Map.of());result.put("preferenceCompleted",preferences.completed(user.studentId()));return result;
    }
}
