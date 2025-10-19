package com.cydeo.service.impl;

import com.cydeo.dto.TaskDTO;
import com.cydeo.entity.Task;
import com.cydeo.enums.Status;
import com.cydeo.exception.*;
import com.cydeo.repository.TaskRepository;
import com.cydeo.service.KeycloakService;
import com.cydeo.service.TaskService;
import com.cydeo.util.MapperUtil;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final MapperUtil mapperUtil;
    private final KeycloakService keycloakService;

    public TaskServiceImpl(TaskRepository taskRepository, MapperUtil mapperUtil, KeycloakService keycloakService) {
        this.taskRepository = taskRepository;
        this.mapperUtil = mapperUtil;
        this.keycloakService = keycloakService;
    }

    @Override
    public TaskDTO create(TaskDTO taskDTO) {

        Optional<Task> foundTask = taskRepository.findByTaskCode(taskDTO.getTaskCode());

        if (foundTask.isPresent()) {
            throw new TaskAlreadyExistsException("Task already exists.");
        }

        String loggedInManager = keycloakService.getUsername();

        checkProjectExists(taskDTO.getProjectCode());
        checkCreateAccessToTaskProject(loggedInManager, taskDTO.getProjectCode());
        checkEmployeeExists(taskDTO.getAssignedEmployee());

        Task taskToSave = mapperUtil.convert(taskDTO, new Task());
        taskToSave.setTaskStatus(Status.OPEN);
        taskToSave.setAssignedDate(LocalDate.now());
        taskToSave.setAssignedManager(loggedInManager);

        Task savedTask = taskRepository.save(taskToSave);

        return mapperUtil.convert(savedTask, new TaskDTO());

    }

    @Override
    public TaskDTO readByTaskCode(String taskCode) {
        Task task = taskRepository.findByTaskCode(taskCode)
                .orElseThrow(() -> new TaskNotFoundException("Task does not exist."));
        checkAccess(task);
        return mapperUtil.convert(task, new TaskDTO());
    }

    @Override
    public List<TaskDTO> readAllTasksByProject(String projectCode) {
        List<Task> tasks = taskRepository.findAllByProjectCode(projectCode);
        return tasks.stream().map(task -> {
            checkAccess(task);
            return mapperUtil.convert(task, new TaskDTO());
        }).collect(Collectors.toList());
    }

    @Override
    public List<TaskDTO> readAllByStatus(Status status) {
        String loggedInUserUsername = keycloakService.getUsername();
        List<Task> tasks = taskRepository.findAllByTaskStatusAndAssignedEmployee(status, loggedInUserUsername);
        return tasks.stream().map(task -> mapperUtil.convert(task, new TaskDTO())).collect(Collectors.toList());
    }

    @Override
    public List<TaskDTO> readAllByStatusIsNot(Status status) {
        String loggedInUserUsername = keycloakService.getUsername();
        List<Task> list = taskRepository.findAllByTaskStatusIsNotAndAssignedEmployee(status, loggedInUserUsername);
        return list.stream().map(obj -> mapperUtil.convert(obj, new TaskDTO())).collect(Collectors.toList());
    }

    @Override
    public Map<String, Integer> getCountsByProject(String projectCode) {

        List<Task> tasks = taskRepository.findAllByProjectCode(projectCode);

        tasks.forEach(this::checkAccess);

        int completedTaskCount = (int) tasks.stream().filter(task -> task.getTaskStatus().equals(Status.COMPLETED)).count();
        int nonCompletedTaskCount = (int) tasks.stream().filter(task -> !task.getTaskStatus().equals(Status.COMPLETED)).count();

        Map<String, Integer> taskCounts = new HashMap<>();

        taskCounts.put("completedTaskCount", completedTaskCount);
        taskCounts.put("nonCompletedTaskCount", nonCompletedTaskCount);

        return taskCounts;

    }

    @Override
    public Integer countNonCompletedByAssignedEmployee(String assignedEmployee) {
        return taskRepository.countNonCompletedByAssignedEmployee(assignedEmployee);
    }

    @Override
    public TaskDTO update(String taskCode, TaskDTO taskDTO) {

        Task foundTask = taskRepository.findByTaskCode(taskCode)
                .orElseThrow(() -> new TaskNotFoundException("Task does not exist."));

        checkEmployeeExists(taskDTO.getAssignedEmployee());
        checkManagerAccessToTaskProject(keycloakService.getUsername(), foundTask);
        checkProjectExists(taskDTO.getProjectCode());

        Task taskToUpdate = mapperUtil.convert(taskDTO, new Task());

        taskToUpdate.setId(foundTask.getId());
        taskToUpdate.setTaskCode(taskCode);
        taskToUpdate.setTaskStatus(taskDTO.getTaskStatus() == null ? foundTask.getTaskStatus() : taskDTO.getTaskStatus());
        taskToUpdate.setAssignedManager(foundTask.getAssignedManager());

        if (!foundTask.getAssignedEmployee().equals(taskDTO.getAssignedEmployee())) {
            taskToUpdate.setAssignedDate(LocalDate.now());
        } else {
            taskToUpdate.setAssignedDate(foundTask.getAssignedDate());
        }

        Task updatedTask = taskRepository.save(taskToUpdate);

        return mapperUtil.convert(updatedTask, new TaskDTO());

    }

    @Override
    public TaskDTO updateStatus(String taskCode, Status status) {

        Task foundTask = taskRepository.findByTaskCode(taskCode)
                .orElseThrow(() -> new TaskNotFoundException("Task does not exist."));

        checkEmployeeAccessToTask(keycloakService.getUsername(), foundTask);

        foundTask.setTaskStatus(status);

        Task updatedTask = taskRepository.save(foundTask);

        return mapperUtil.convert(updatedTask, new TaskDTO());

    }

    @Override
    public void completeByProject(String projectCode) {

        List<Task> tasks = taskRepository.findAllByProjectCode(projectCode);

        tasks.forEach(taskToComplete -> {
            checkAccess(taskToComplete);
            taskToComplete.setTaskStatus(Status.COMPLETED);
            taskRepository.save(taskToComplete);
        });

    }

    @Override
    public void delete(String taskCode) {

        Task taskToDelete = taskRepository.findByTaskCode(taskCode)
                .orElseThrow(() -> new TaskNotFoundException("Task does not exist."));

        checkManagerAccessToTaskProject(keycloakService.getUsername(), taskToDelete);

        taskToDelete.setIsDeleted(true);
        taskToDelete.setTaskCode(taskCode + "-" + taskToDelete.getId());

        taskRepository.save(taskToDelete);

    }

    @Override
    public void deleteByProject(String projectCode) {

        List<Task> tasks = taskRepository.findAllByProjectCode(projectCode);

        tasks.forEach(taskToDelete -> {
            checkAccess(taskToDelete);
            taskToDelete.setIsDeleted(true);
            taskToDelete.setTaskCode(taskToDelete.getTaskCode() + "-" + taskToDelete.getId());
            taskRepository.save(taskToDelete);
        });

    }

    private void checkProjectExists(String projectCode) {

        //TODO Check if project exists or not by asking about it to project-service

    }

    private void checkEmployeeExists(String assignedEmployee) {

        //TODO Check if employee exists or not by asking about it to user-service

    }

    private void checkAccess(Task task) {

        String loggedInUserUsername = keycloakService.getUsername();

        if (keycloakService.hasClientRole(loggedInUserUsername, "Manager")) {
            checkManagerAccessToTaskProject(loggedInUserUsername, task);
        } else if (keycloakService.hasClientRole(loggedInUserUsername, "Employee")) {
            checkEmployeeAccessToTask(loggedInUserUsername, task);
        } else {
            throw new AccessDeniedException("Access is denied");
        }

    }

    private void checkCreateAccessToTaskProject(String loggedInUserUsername, String projectCode) {

        //TODO Check if logged in user has access to the project of the task to create tasks for that project
        //     by asking about it to project-service

    }

    private void checkManagerAccessToTaskProject(String loggedInUserUsername, Task task) {
        String taskManager = task.getAssignedManager();
        if (!loggedInUserUsername.equals(taskManager)) {
            throw new ProjectAccessDeniedException("Access denied, make sure that you are working on your own project.");
        }
    }

    private void checkEmployeeAccessToTask(String loggedInUserUsername, Task task) {
        String taskEmployee = task.getAssignedEmployee();
        if (!loggedInUserUsername.equals(taskEmployee)) {
            throw new TaskAccessDeniedException("Access denied, make sure that you are working on your own task.");
        }
    }

}
