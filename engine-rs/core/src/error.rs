use thiserror::Error;

#[derive(Debug, Error)]
pub enum RouteForgeError {
    #[error("invalid graph: {0}")]
    InvalidGraph(String),
    #[error("unknown profile: {0}")]
    UnknownProfile(String),
    #[error("no path found")]
    NoPath,
    #[error("empty graph")]
    EmptyGraph,
}

pub type Result<T> = std::result::Result<T, RouteForgeError>;
