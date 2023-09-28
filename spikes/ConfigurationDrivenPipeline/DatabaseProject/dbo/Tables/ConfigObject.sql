CREATE TABLE [dbo].[ConfigObject] (
    [ObjectId]         INT           IDENTITY (1, 1) NOT NULL,
    [ObjectSchema]     VARCHAR (500) NOT NULL,
    [ObjectName]       VARCHAR (500) NOT NULL,
    [ObjectType]       VARCHAR (500) NULL,
    [ObjectConfig]     VARCHAR (500) NULL,
    [LoadType]         VARCHAR (1)   NULL,
    [LastModifiedTime] DATETIME      NULL,
    [SourceId]         INT           NOT NULL,
    CONSTRAINT [PK_ConfigObject] PRIMARY KEY CLUSTERED ([ObjectSchema] ASC, [ObjectName] ASC, [SourceId] ASC),
    FOREIGN KEY ([SourceId]) REFERENCES [dbo].[ConfigSource] ([SourceId])
);


GO

