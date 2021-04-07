package tech.kinori.eclipse.p2mvn.maven;

import java.util.Objects;

public class MavenCoordinates {

	public MavenCoordinates(
			String groupId,
			String artifactId,
			String version) {
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
	}

	/**
	 * Returns a format String, callers need to provide
	 * 1. path to jar
	 * 2. repoid
	 * 3. repo url
	 */
	public String deployCmd(String packaging, boolean batch) {
		String prefix = "";
		if (batch) {
			prefix = "call ";
		}
		StringBuilder builder = new StringBuilder();
		if (batch) {
			builder.append("call ");
		}
		builder.append("mvn deploy:deploy-file -DgroupId=");
		builder.append(this.groupId);
		builder.append(" -DartifactId=");
		builder.append(this.artifactId);
		builder.append(" -Dversion=");
		builder.append(this.version);
		builder.append(" -Dpackaging=");
		builder.append(packaging);
		builder.append(" -Dfile=%s");
		builder.append(" -DrepositoryId=%s");
		builder.append(" -Durl=%s");
		builder.append(System.lineSeparator());
		return builder.toString();
	}

	public String installCmd(String packaging, boolean batch) {
		StringBuilder builder = new StringBuilder();
		if (batch) {
			builder.append("call ");
		}
		builder.append("mvn install:install-file -DgroupId=");
		builder.append(this.groupId);
		builder.append(" -DartifactId=");
		builder.append(this.artifactId);
		builder.append(" -Dversion=");
		builder.append(this.version);
		builder.append(" -Dpackaging=");
		builder.append(packaging);
		builder.append(" -Dfile=%s");
		builder.append(System.lineSeparator());
		return builder.toString();
	}

	public String groupId() {
		return this.groupId;
	}



	@Override
	public String toString() {
		return String.format("%s:%s:%s", this.groupId, this.artifactId, this.version);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		MavenCoordinates that = (MavenCoordinates) o;
		return Objects.equals(groupId, that.groupId)
				&& Objects.equals(artifactId, that.artifactId)
				&& Objects.equals(version, that.version);
	}

	@Override
	public int hashCode() {
		return Objects.hash(groupId, artifactId, version);
	}



    private final String groupId;
	private final String artifactId;
	private final String version;


}
