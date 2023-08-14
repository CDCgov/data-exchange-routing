CREATE TABLE [dbo].[ConfigTarget] (
    [TargetId]     INT           IDENTITY (1, 1) NOT NULL,
    [TargetName]   VARCHAR (500) NULL,
    [TargetType]   VARCHAR (500) NULL,
    [TargetConfig] VARCHAR (500) NULL,
    [TriggerId]    INT           NULL,
    PRIMARY KEY CLUSTERED ([TargetId] ASC),
    FOREIGN KEY ([TriggerId]) REFERENCES [dbo].[ConfigTrigger] ([TriggerId])
);


GO

