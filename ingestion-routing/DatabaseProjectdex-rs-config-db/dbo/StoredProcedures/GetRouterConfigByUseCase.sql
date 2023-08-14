CREATE PROCEDURE [dbo].[GetRouterConfigByUseCase] @UseCase nvarchar(100)
AS 
    SELECT TOP 1 [ConfigurationJSON]
    FROM [dbo].[RouterConfigurations]
    WHERE [UseCaseName] = @UseCase

GO

