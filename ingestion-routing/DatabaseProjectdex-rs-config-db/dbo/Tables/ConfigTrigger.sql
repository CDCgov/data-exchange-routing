CREATE TABLE [dbo].[ConfigTrigger] (
    [TriggerId]   INT           IDENTITY (1, 1) NOT NULL,
    [TriggerName] VARCHAR (500) NULL,
    [TriggerType] VARCHAR (500) NULL,
    PRIMARY KEY CLUSTERED ([TriggerId] ASC),
    UNIQUE NONCLUSTERED ([TriggerName] ASC)
);


GO

