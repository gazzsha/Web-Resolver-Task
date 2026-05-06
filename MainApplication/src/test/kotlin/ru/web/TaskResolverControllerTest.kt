package ru.web

import com.fasterxml.jackson.databind.ObjectMapper
import model.StartTaskRequest
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.security.JwtTokenProvider
import ru.taskresolver.service.process.TaskResolverProcessService
import ru.taskresolver.web.TaskResolverController
import java.util.UUID

@WebMvcTest(controllers = [TaskResolverController::class])
@AutoConfigureMockMvc(addFilters = false)
class TaskResolverControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var om: ObjectMapper

    @MockBean
    lateinit var taskResolverProcessService: TaskResolverProcessService

    @MockBean
    lateinit var jwtTokenProvider: JwtTokenProvider

    @Test
    fun `startTask returns 200 with submission id and success true`() {
        val submissionId = UUID.randomUUID()
        val testId = UUID.randomUUID()

        given(taskResolverProcessService.processStartTaskToResolve(anyNonNull()))
            .willReturn(submissionId)

        val body = om.writeValueAsString(
            mapOf(
                "testId" to testId.toString(),
                "language" to "java",
                "code" to "class Solution {}",
            ),
        )

        mvc.perform(
            patch("/api/v1/task-resolver/task/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(submissionId.toString()))
            .andExpect(jsonPath("$.result.success").value(true))

        verify(taskResolverProcessService).processStartTaskToResolve(anyNonNull())
    }
}
