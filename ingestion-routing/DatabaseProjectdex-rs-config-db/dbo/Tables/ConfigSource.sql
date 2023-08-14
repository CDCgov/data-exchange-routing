CREATE TABLE [dbo].[ConfigSource] (
    [SourceId]     INT           IDENTITY (1, 1) NOT NULL,
    [SourceName]   VARCHAR (500) NOT NULL,
    [SourceType]   VARCHAR (500) NULL,
    [SourceConfig] VARCHAR (500) NULL,
    [TriggerId]    INT           NULL,
    PRIMARY KEY CLUSTERED ([SourceId] ASC),
    FOREIGN KEY ([TriggerId]) REFERENCES [dbo].[ConfigTrigger] ([TriggerId]),
    UNIQUE NONCLUSTERED ([SourceName] ASC)
);


GO

