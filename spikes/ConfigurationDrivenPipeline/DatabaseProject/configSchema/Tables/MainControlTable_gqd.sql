CREATE TABLE [configSchema].[MainControlTable_gqd] (
    [Id]                           INT            IDENTITY (1, 1) NOT NULL,
    [SourceObjectSettings]         NVARCHAR (MAX) NULL,
    [SourceConnectionSettingsName] VARCHAR (MAX)  NULL,
    [CopySourceSettings]           NVARCHAR (MAX) NULL,
    [SinkObjectSettings]           NVARCHAR (MAX) NULL,
    [SinkConnectionSettingsName]   VARCHAR (MAX)  NULL,
    [CopySinkSettings]             NVARCHAR (MAX) NULL,
    [CopyActivitySettings]         NVARCHAR (MAX) NULL,
    [TopLevelPipelineName]         VARCHAR (MAX)  NULL,
    [TriggerName]                  NVARCHAR (MAX) NULL,
    [DataLoadingBehaviorSettings]  NVARCHAR (MAX) NULL,
    [TaskId]                       INT            NULL,
    [CopyEnabled]                  BIT            NULL,
    PRIMARY KEY CLUSTERED ([Id] ASC)
);


GO

