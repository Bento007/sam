package org.broadinstitute.dsde.workbench.sam.config

/**
  * Created by dvoet on 5/26/17.
  */
final case class DirectoryConfig(
    directoryUrl: String,
    user: String,
    password: String,
    baseDn: String,
    enabledUsersGroupDn: String,
    connectionPoolSize: Int = 15,
    backgroundConnectionPoolSize: Int = 5,
    memberOfCache: CacheConfig,
    resourceCache: CacheConfig)

final case class CacheConfig(maxEntries: Long, timeToLive: java.time.Duration)
