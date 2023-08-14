CREATE TABLE [dbo].[RouterConfigurations] (
    [Id]                INT            IDENTITY (1, 1) NOT NULL,
    [UseCaseName]       NVARCHAR (255) NULL,
    [ConfigurationJSON] NVARCHAR (MAX) NULL,
    PRIMARY KEY CLUSTERED ([Id] ASC)
);


GO

