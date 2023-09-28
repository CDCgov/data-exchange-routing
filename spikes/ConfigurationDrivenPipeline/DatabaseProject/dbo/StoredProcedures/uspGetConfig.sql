 CREATE PROCEDURE dbo.uspGetConfig @TriggerName nvarchar(100)
AS 
select sub.*,
O.ObjectId AS ObjectId,
O.ObjectSchema AS ObjectSchema,
O.ObjectName AS ObjectName,
O.ObjectType AS ObjectType,
O.ObjectConfig AS ObjectConfig,
O.LoadType AS LoadType ,
O.LastModifiedTime AS LastModifiedTime
 from
(select 
TR.TriggerName AS TriggerName,
S.SourceId AS SourceId,
S.SourceName  AS SourceName,
S.SourceType AS SourceType,
S.SourceConfig AS SourceConfig,
T.TargetName AS TargetName,
T.TargetType AS TargetType,
T.TargetConfig AS TargetConfig

 from [dbo].[ConfigTrigger] TR ,
 [dbo].[ConfigSource] S,
 [dbo].[ConfigTarget] T

 where 
 TR.TriggerId=S.TriggerId AND TR.TriggerId=T.TriggerId AND 
 TR.TriggerName=@TriggerName) as sub LEFT JOIN
  [dbo].[ConfigObject] O ON sub.SourceId=O.SourceId

GO

