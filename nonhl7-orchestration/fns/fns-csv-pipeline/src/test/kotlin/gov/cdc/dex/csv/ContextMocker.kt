package gov.cdc.dex.csv

import com.microsoft.azure.functions.ExecutionContext

import java.util.logging.Logger
import java.util.logging.Level

import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock;


object ContextMocker {
    fun mockExecutionContext(): ExecutionContext {
        val mockContext : ExecutionContext = Mockito.mock(ExecutionContext::class.java);
        val logger : Logger = Mockito.mock(Logger::class.java);

        Mockito.`when`(mockContext.logger).thenReturn(logger)
        Mockito.`when`(logger.info(Mockito.anyString())).thenAnswer({log(Level.INFO,it.getArgument(0))})
        Mockito.`when`(logger.warning(Mockito.anyString())).thenAnswer({log(Level.WARNING,it.getArgument(0))})
        Mockito.`when`(logger.log(Mockito.any(),Mockito.anyString())).thenAnswer({log(it.getArgument(0),it.getArgument(1))})
        Mockito.`when`(logger.log(Mockito.any(),Mockito.anyString(), Mockito.any(Throwable::class.java))).thenAnswer({log(it.getArgument(0),it.getArgument(1),it.getArgument(2))})
        return mockContext
    }

    private fun log(level:Level, msg:String){
        println("\n*MOCK*[$level] $msg");
    }

    private fun log(level:Level, msg:String, e:Throwable){
        println("\n*MOCK*[$level] $msg : ${e.localizedMessage}");
    }
}