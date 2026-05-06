package ru.web

import com.fasterxml.jackson.databind.ObjectMapper
import model.Difficulty
import model.TaskInfo
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.security.JwtTokenProvider
import ru.taskresolver.service.TestService
import ru.taskresolver.web.TasksController
import java.util.UUID

// Security filters disabled: authentication enforcement is covered by AuthFlowIntegrationTest.
// JwtTokenProvider is mocked to satisfy the JwtAuthenticationFilter bean dependency in context.
@WebMvcTest(controllers = [TasksController::class])
@AutoConfigureMockMvc(addFilters = false)
class TasksControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var om: ObjectMapper

    @MockBean
    lateinit var testService: TestService

    @MockBean
    lateinit var jwtTokenProvider: JwtTokenProvider

    private val taskId1 = UUID.randomUUID()
    private val taskId2 = UUID.randomUUID()

    private fun taskInfo(id: UUID, title: String) =
        TaskInfo(id, title, "Description of $title", Difficulty.EASY)

    // -------------------------------------------------------------------------
    // GET /api/v1/tasks
    // -------------------------------------------------------------------------

    @Test
    fun `getAllTasks returns 200 with json array`() {
        given(testService.getAllTasks()).willReturn(
            listOf(taskInfo(taskId1, "Two Sum"), taskInfo(taskId2, "Reverse String")),
        )

        mvc.perform(get("/api/v1/tasks").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].title").value("Two Sum"))
            .andExpect(jsonPath("$[1].title").value("Reverse String"))
    }

    @Test
    fun `getAllTasks returns 200 with empty array when no tasks`() {
        given(testService.getAllTasks()).willReturn(emptyList())

        mvc.perform(get("/api/v1/tasks").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/tasks/{taskId}
    // -------------------------------------------------------------------------

    @Test
    fun `getTaskById returns 200 with task details`() {
        val info = taskInfo(taskId1, "Two Sum")
        given(testService.getTaskById(taskId1)).willReturn(info)

        mvc.perform(get("/api/v1/tasks/$taskId1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.testId").value(taskId1.toString()))
            .andExpect(jsonPath("$.title").value("Two Sum"))
            .andExpect(jsonPath("$.difficulty").value("Easy"))
    }
}
